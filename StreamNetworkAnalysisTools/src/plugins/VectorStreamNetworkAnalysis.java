/*
 * Copyright (C) 2016 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package plugins;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.LinkedList;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.geospatialfiles.ShapeFile;
import whitebox.geospatialfiles.shapefile.*;
import whitebox.geospatialfiles.VectorLayerInfo;
import whitebox.geospatialfiles.shapefile.attributes.*;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;
import whitebox.structures.KdTree;
//import whitebox.structures.XYZPoint;
import whitebox.structures.BooleanBitArray2D;
import whitebox.structures.IntArray2D;

/**
 *
 * @author johnlindsay
 */
public class VectorStreamNetworkAnalysis implements WhiteboxPlugin {

    private WhiteboxPluginHost pluginHost = null;
    private String[] args;

    private String name = "VectorStreamNetworkAnalysis";
    private String descriptiveName = "Vector Stream Network Analysis";
    private String description = "Calculates stream network geometry from vector streams";
    private String[] toolboxes = {"StreamAnalysis"};

    List<EndPoint> endPoints = new ArrayList<>();
    Link[] links;
    List<Node> nodes = new ArrayList<>();

    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name
     * containing no spaces.
     *
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer
     * name (containing spaces) and is used in the interface to list the tool.
     *
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
        return descriptiveName;
    }

    /**
     * Used to retrieve a short description of what the plugin tool does.
     *
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
        return description;
    }

    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     *
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
        return toolboxes;
    }

    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the
     * class that the plugin will send all feedback messages, progress updates,
     * and return objects.
     *
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        pluginHost = host;
    }

    /**
     * Used to communicate feedback pop-up messages between a plugin tool and
     * the main Whitebox user-interface.
     *
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (pluginHost != null) {
            pluginHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }

    /**
     * Used to communicate a return object from a plugin tool to the main
     * Whitebox user-interface.
     *
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (pluginHost != null) {
            pluginHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";

    /**
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (pluginHost != null && ((progress != previousProgress)
                || (!progressLabel.equals(previousProgressLabel)))) {
            pluginHost.updateProgress(progressLabel, progress);
        } else {
            System.out.println(progressLabel + " " + progress + "%");
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }

    /**
     * Used to communicate a progress update between a plugin tool and the main
     * Whitebox user interface.
     *
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (pluginHost != null && progress != previousProgress) {
            pluginHost.updateProgress(progress);
        } else {
            System.out.println(progress + "%");
        }
        previousProgress = progress;
    }

    /**
     * Sets the arguments (parameters) used by the plugin.
     *
     * @param args An array of string arguments.
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }

    private boolean cancelOp = false;

    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     *
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }

    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }

    private boolean amIActive = false;

    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     *
     * @return a boolean describing whether or not the plugin is actively being
     * used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    private void pluginComplete() {
        if (pluginHost != null) {
            pluginHost.pluginComplete();
        } else {
            System.out.println("Complete!");
        }
    }

    private void logException(String msg, Exception e) {
        if (pluginHost != null) {
            pluginHost.logException(msg, e);
        } else {
            System.err.println(msg + "\n" + e.getMessage());
        }
    }

    /**
     * Used to execute this plugin tool.
     */
    @Override
    public void run() {
        amIActive = true;

        // Declare some variables
        int progress, oldProgress, col, row;
        int n, j;
        double x, y, z, z1, z2; //, x1, x2, y1, y2;
        double length;
        double distMultiplier = 1.0;
        Object[] rowData;
        int count = 0;
        double[][] points;
        int[] partData;
        int startingPointInPart, endingPointInPart;
        int i, numParts, numPoints, recNum, part, p;
        int outletNum = 1;
        int featureNum = 0;
        List<KdTree.Entry<Integer>> results;
        List<KdTree.Entry<Integer>> resultsLakes;
        double[] entry;
        //List<Integer> outletsLinkIDs = new ArrayList<>();

        KdTree<Integer> pointsTree;
        whitebox.geospatialfiles.shapefile.PolyLine wbGeometry;

        // Deal with the input arguments
        if (args.length < 5) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }

        String streamsFile = args[0];
        String demFile = args[1];
        String lakesFile = args[2];
        String outputFile = args[3];
        double snapDistance = Double.parseDouble(args[4]);

        try {
            // read the input image
            WhiteboxRaster dem = new WhiteboxRaster(demFile, "r");
            dem.setForceAllDataInMemory(true);
            double nodata = dem.getNoDataValue();
            int rows = dem.getNumberRows();
            int cols = dem.getNumberColumns();

            // If the input DEM is in geographic coordinates, the snapdistance 
            // will need to be converted.
            if (dem.getXYUnits().toLowerCase().contains("deg")) {
                double midLat = (dem.getNorth() - dem.getSouth()) / 2.0;
                if (midLat <= 90 && midLat >= -90) {
                    midLat = Math.toRadians(midLat);
                    double a = 6378137.0;
                    double b = 6356752.314;
                    double e2 = (a * a - b * b) / (a * a);
                    double num = (Math.PI * a * Math.cos(midLat));
                    double denum = (180 * Math.sqrt((1 - e2 * Math.sin(midLat) * Math.sin(midLat))));
                    double longDegDist = (num / denum);
                    double latDegDist = 111132.954 - 559.822 * Math.cos(2.0 * midLat) + 1.175 * Math.cos(4.0 * midLat);
                    distMultiplier = (longDegDist + latDegDist) / 2.0;

                    snapDistance = snapDistance / distMultiplier;
//                    System.out.println(snapDistance);
                }
            }

            snapDistance = snapDistance * snapDistance;

            ShapeFile input = new ShapeFile(streamsFile);
            ShapeType shapeType = input.getShapeType();
            if (shapeType.getBaseType() != ShapeType.POLYLINE) {
                showFeedback("The input shapefile should be of a POLYLINE ShapeType.");
                return;
            }
            int numFeatures = input.getNumberOfRecords();

            ShapeFile lakes;
            boolean lakesUsed = false;
            int numLakes = 0;
            KdTree<Integer> lakesTree = new KdTree.SqrEuclid<>(2, null);
            int[] lakesNodeIDs = new int[0];
            if (!lakesFile.toLowerCase().contains("not specified")) {
                lakes = new ShapeFile(lakesFile);
                shapeType = lakes.getShapeType();
                if (shapeType.getBaseType() != ShapeType.POLYGON) {
                    showFeedback("The input lakes shapefile should be of a Polygon ShapeType.");
                    return;
                }
                lakesUsed = true;

                numLakes = lakes.getNumberOfRecords();
                lakesNodeIDs = new int[numLakes];
                for (i = 0; i < numLakes; i++) {
                    lakesNodeIDs[i] = -1;
                }

                // read all of the lake vertices into a k-d tree.
                lakesTree = lakes.getKdTree();
            }

            /* Find all exterior nodes in the network. This includes nodes that
               are not associated with bifurcations as well as nodes where only
               one of the ajoining links overlaps with the DEM. 
            
               To do this, first read in the shapefile, retreiving each starting
               and ending nodes (called end-nodes) of the contained lines. Place 
               the end points into a k-d tree. Visit each site and count the 
               number of end points at each node. Those with more than one are
               bifurcations and exterior nodes have only one end point.
            
             */
            // first enter the line end-nodes into a kd-tree
            // create the output file
            DBFField[] fields = new DBFField[13];

            fields[0] = new DBFField();
            fields[0].setName("FID");
            fields[0].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[0].setFieldLength(6);
            fields[0].setDecimalCount(0);

            fields[1] = new DBFField();
            fields[1].setName("OUTLET");
            fields[1].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[1].setFieldLength(10);
            fields[1].setDecimalCount(0);

            fields[2] = new DBFField();
            fields[2].setName("TUCL");
            fields[2].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[2].setFieldLength(10);
            fields[2].setDecimalCount(3);

            fields[3] = new DBFField();
            fields[3].setName("MAXUPSDIST");
            fields[3].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[3].setFieldLength(10);
            fields[3].setDecimalCount(3);

            fields[4] = new DBFField();
            fields[4].setName("DS_NODES");
            fields[4].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[4].setFieldLength(6);
            fields[4].setDecimalCount(0);

            fields[5] = new DBFField();
            fields[5].setName("DIST2MOUTH");
            fields[5].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[5].setFieldLength(10);
            fields[5].setDecimalCount(3);

            fields[6] = new DBFField();
            fields[6].setName("HORTON");
            fields[6].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[6].setFieldLength(6);
            fields[6].setDecimalCount(0);

            fields[7] = new DBFField();
            fields[7].setName("STRAHLER");
            fields[7].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[7].setFieldLength(6);
            fields[7].setDecimalCount(0);

            fields[8] = new DBFField();
            fields[8].setName("SHREVE");
            fields[8].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[8].setFieldLength(10);
            fields[8].setDecimalCount(3);

            fields[9] = new DBFField();
            fields[9].setName("HACK");
            fields[9].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[9].setFieldLength(6);
            fields[9].setDecimalCount(0);

            fields[10] = new DBFField();
            fields[10].setName("MAINSTEM");
            fields[10].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[10].setFieldLength(1);
            fields[10].setDecimalCount(0);

            fields[11] = new DBFField();
            fields[11].setName("TRIB_ID");
            fields[11].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[11].setFieldLength(6);
            fields[11].setDecimalCount(0);

            fields[12] = new DBFField();
            fields[12].setName("DISCONT");
            fields[12].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[12].setFieldLength(4);
            fields[12].setDecimalCount(0);

            ShapeFile output = new ShapeFile(outputFile, ShapeType.POLYLINE, fields);

            fields = new DBFField[2];

            fields[0] = new DBFField();
            fields[0].setName("FID");
            fields[0].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[0].setFieldLength(6);
            fields[0].setDecimalCount(0);

            fields[1] = new DBFField();
            fields[1].setName("TYPE");
            fields[1].setDataType(DBFField.DBFDataType.STRING);
            fields[1].setFieldLength(14);

//            fields[2] = new DBFField();
//            fields[2].setName("DISCOVERNG");
//            fields[2].setDataType(DBFField.DBFDataType.NUMERIC);
//            fields[2].setFieldLength(6);
//            fields[2].setDecimalCount(0);
//
//            fields[2] = new DBFField();
//            fields[2].setName("DISCOVERED");
//            fields[2].setDataType(DBFField.DBFDataType.NUMERIC);
//            fields[2].setFieldLength(6);
//            fields[2].setDecimalCount(0);
            ShapeFile outputNodes = new ShapeFile(outputFile.replace(".shp", "_nodes.shp"), ShapeType.POINT, fields);

            updateProgress("Pre-processing", 0);

            ///////////////////
            // Find edge cells
            ///////////////////
            int rowsLessOne = rows - 1;
            int nc; // neighbouring cell
            int[] dX = new int[]{1, 1, 1, 0, -1, -1, -1, 0};
            int[] dY = new int[]{-1, 0, 1, 1, 1, 0, -1, -1};
            BooleanBitArray2D isEdgeCell = new BooleanBitArray2D(rows, cols);

            oldProgress = -1;
            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    z = dem.getValue(row, col);
                    if (z != nodata) {
                        for (nc = 0; nc < 8; nc++) {
                            if (dem.getValue(row + dY[nc], col + dX[nc]) == nodata) {
                                isEdgeCell.setValue(row, col, true);
                                break;
                            }
                        }
                    }
                }
                progress = (int) (100f * row / rowsLessOne);
                if (progress > oldProgress) {
                    updateProgress("Finding DEM edge cells:", progress);
                    oldProgress = progress;

                    // check to see if the user has requested a cancellation
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                }
            }

            /////////////////////////////
            // count the number of parts
            /////////////////////////////
            int numLinks = 0;
            int totalVertices = 0;
            for (ShapeFileRecord record : input.records) {
                numLinks += record.getGeometry().getParts().length;
                totalVertices += record.getGeometry().getPoints().length;
            }

            PriorityQueue<EndPoint> streamQueue = new PriorityQueue<>(totalVertices);
            links = new Link[numLinks];
            boolean[] crossesDemEdge = new boolean[numLinks];
            boolean[] isFeatureMapped = new boolean[numLinks];

            pointsTree = new KdTree.SqrEuclid<>(2, null);

            /////////////////////////////////////////////////////////////
            // Read the end-nodes into the KD-tree. 
            // Find potential outlet nodes and push them into the queue.
            /////////////////////////////////////////////////////////////
            boolean crossesValidData;
            boolean crossesNodata;
            boolean edgeValue1, edgeValue2;
            featureNum = -1;
            oldProgress = -1;
            int currentEndPoint = 0;
            int k = 0;
            for (ShapeFileRecord record : input.records) {
                recNum = record.getRecordNumber();
                points = record.getGeometry().getPoints();
                numPoints = points.length;
                partData = record.getGeometry().getParts();
                numParts = partData.length;
                for (part = 0; part < numParts; part++) {
                    featureNum++;
                    startingPointInPart = partData[part];
                    if (part < numParts - 1) {
                        endingPointInPart = partData[part + 1] - 1;
                    } else {
                        endingPointInPart = numPoints - 1;
                    }

                    length = 0;
                    for (i = startingPointInPart + 1; i <= endingPointInPart; i++) {
                        length += distMultiplier * Math.sqrt((points[i][0] - points[i - 1][0])
                                * (points[i][0] - points[i - 1][0]) + (points[i][1] - points[i - 1][1])
                                * (points[i][1] - points[i - 1][1]));
                    }

                    crossesValidData = false;
                    crossesNodata = false;
                    for (i = startingPointInPart; i <= endingPointInPart; i++) {

                        row = dem.getRowFromYCoordinate(points[i][1]);
                        col = dem.getColumnFromXCoordinate(points[i][0]);
                        z = dem.getValue(row, col);
                        if (z != nodata) {
                            crossesValidData = true;
                            isFeatureMapped[featureNum] = true;
                        }
                        if (isEdgeCell.getValue(row, col)) {
                            crossesNodata = true;
                        }
                        if (z == nodata) {
                            crossesNodata = true;
                        }
                    }

                    //linkLengths[featureNum] = length;
                    if (crossesNodata && crossesValidData) {
                        crossesDemEdge[featureNum] = true;
                    }

                    row = dem.getRowFromYCoordinate(points[startingPointInPart][1]);
                    col = dem.getColumnFromXCoordinate(points[startingPointInPart][0]);
                    z1 = dem.getValue(row, col);
                    edgeValue1 = isEdgeCell.getValue(row, col);

                    row = dem.getRowFromYCoordinate(points[endingPointInPart][1]);
                    col = dem.getColumnFromXCoordinate(points[endingPointInPart][0]);
                    z2 = dem.getValue(row, col);
                    edgeValue2 = isEdgeCell.getValue(row, col);

                    if (isFeatureMapped[featureNum]) {
                        x = points[startingPointInPart][0];
                        y = points[startingPointInPart][1];
                        entry = new double[]{x, y};
                        pointsTree.addPoint(entry, currentEndPoint);
                        EndPoint e1 = new EndPoint(currentEndPoint, featureNum, x, y, z1); //links.length, x, y, z1);
                        endPoints.add(e1);

                        x = points[endingPointInPart][0];
                        y = points[endingPointInPart][1];
                        entry = new double[]{x, y};
                        pointsTree.addPoint(entry, currentEndPoint + 1);
                        EndPoint e2 = new EndPoint(currentEndPoint + 1, featureNum, x, y, z2); //links.length, x, y, z2);
                        endPoints.add(e2);
                        //k++;

                        // This is a possible outlet.
                        if (crossesDemEdge[featureNum]) {
                            // rules for deciding with end point of the link is the actual outlet
//                            if ((z1 == nodata && z2 != nodata) || (edgeValue1 && (!edgeValue2 && z2 != nodata)) || z1 < z2) {
//                            if ((z1 == nodata && z2 != nodata) || (edgeValue1 && !edgeValue2) || (z1 < z2 && z1 != nodata)) {
//                                streamQueue.add(e1);
//                                e1.outflowingNode = true;
//                                
//                                whitebox.geospatialfiles.shapefile.Point pointOfInterest
//                                    = new whitebox.geospatialfiles.shapefile.Point(e1.x, e1.y);
//                                rowData = new Object[2];
//                                rowData[0] = new Double(1); //new Double(e1.nodeID);
//                                rowData[1] = "outlet";
//                                outputNodes.addRecord(pointOfInterest, rowData);
//                            } else {
//                                streamQueue.add(e2);
//                                e2.outflowingNode = true;
//                                whitebox.geospatialfiles.shapefile.Point pointOfInterest
//                                    = new whitebox.geospatialfiles.shapefile.Point(e2.x, e2.y);
//                                rowData = new Object[2];
//                                rowData[0] = new Double(2); //new Double(e2.nodeID);
//                                rowData[1] = "outlet";
//                                outputNodes.addRecord(pointOfInterest, rowData);
//                            }

                            // rules for deciding which end point of the link is the actual outlet
                            EndPoint e3 = e1;
                            if (z1 == nodata && z2 != nodata) { // first rule: one of end points is nodata and not the other
                                e3 = e1;
                            } else if (z2 == nodata && z1 != nodata) {
                                e3 = e2;
                            } else if (edgeValue1 && (!edgeValue2 && z2 != nodata)) { // second rule: one of the end points is and edge cell and not the other
                                e3 = e1;
                            } else if (edgeValue2 && (!edgeValue1 && z1 != nodata)) {
                                e3 = e2;
                            } else if (z1 < z2 && z2 != nodata) { // third rule: one of the points is lower
                                e3 = e1;
                            } else if (z2 < z1 && z1 != nodata) {
                                e3 = e2;
                            }

                            streamQueue.add(e3);
                            e3.outflowingNode = true;
//                            whitebox.geospatialfiles.shapefile.Point pointOfInterest
//                                = new whitebox.geospatialfiles.shapefile.Point(e3.x, e3.y);
//                            rowData = new Object[2];
//                            rowData[0] = new Double(2); //new Double(e2.nodeID);
//                            rowData[1] = "outlet";
//                            outputNodes.addRecord(pointOfInterest, rowData);

                        }
                        links[featureNum] = new Link(featureNum, currentEndPoint, currentEndPoint + 1, length);
                        currentEndPoint += 2;
                    }
                }

                progress = (int) (100f * recNum / numFeatures);
                if (progress != oldProgress) {
                    updateProgress("Characterizing nodes (loop 1 of 2):", progress);
                    oldProgress = progress;
                    // check to see if the user has requested a cancellation
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                }
            }

            isEdgeCell = null;

            boolean[] visitedEndPoint = new boolean[endPoints.size()];
            EndPoint e, e2;
            progress = -1;

            for (i = 0; i < endPoints.size(); i++) {
                if (!visitedEndPoint[i]) {
                    e = endPoints.get(i);
                    x = e.x;
                    y = e.y;
                    z = e.z;

                    entry = new double[]{x, y};
                    results = pointsTree.neighborsWithinRange(entry, snapDistance);

                    if (!results.isEmpty()) {
                        if (results.size() == 1 && lakesUsed && !e.outflowingNode) { // end node
                            visitedEndPoint[i] = true;
                            // check to see if it's a lake inlet/outlet
                            resultsLakes = lakesTree.neighborsWithinRange(entry, snapDistance);
                            if (!resultsLakes.isEmpty()) {
                                // which lake is this stream endnode connected to?
                                int lakeNum = (int) resultsLakes.get(0).value;

                                // does this lake already have a node?
                                int nodeNum = lakesNodeIDs[lakeNum];
                                if (nodeNum != -1) { // yes it does
                                    nodes.get(nodeNum).addPoint(i);
                                    endPoints.get(i).nodeID = nodeNum;
                                } else { // no, create a new node for it
                                    Node node = new Node();
                                    node.addPoint(i);
                                    endPoints.get(i).nodeID = nodes.size();
                                    lakesNodeIDs[lakeNum] = nodes.size();
                                    nodes.add(node);
                                }
                            } else {
                                Node node = new Node();
                                node.addPoint(i);
                                endPoints.get(i).nodeID = nodes.size();
                                nodes.add(node);
                                visitedEndPoint[i] = true;
                            }
                        } else {
                            Node node = new Node();
                            for (j = 0; j < results.size(); j++) {
                                currentEndPoint = (int) results.get(j).value;
                                node.addPoint(currentEndPoint);
                                visitedEndPoint[currentEndPoint] = true;
                                endPoints.get(currentEndPoint).nodeID = nodes.size();
                            }
                            nodes.add(node);
                        }
                    }
                }

                progress = (int) (100f * i / endPoints.size());
                if (progress != oldProgress) {
                    updateProgress("Characterizing nodes (loop 2 of 2):", progress);
                    oldProgress = progress;
                    // check to see if the user has requested a cancellation
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                }
            }

            /////////////////////////////////////////////////////////////////////
            // Priority-queue operation, progresses from downstream to upstream.
            // The flow-directions among connected arcs is determined in this step.
            /////////////////////////////////////////////////////////////////////
            Node node;
            Link link;
            int epNum;
            int numDownstreamNodes;
            double distToOutlet;
            int numPopped = 0;
            int outletID;
            int outletLinkID;
            oldProgress = -1;
            while (!streamQueue.isEmpty()) {
                numPopped++;
                e = streamQueue.poll();
                link = links[e.linkID];
                numDownstreamNodes = link.numDownstreamNodes;
                distToOutlet = link.distToOutlet;
                outletID = link.outlet;
                if (outletID == -1) {
                    links[e.linkID].outlet = outletNum;
                    //outletsLinkIDs.add(e.linkID);
                    outletID = outletNum;
                    outletNum++;
                    links[e.linkID].isOutletLink = true;
                    links[e.linkID].outletLinkID = e.linkID;
//                    links[e.linkID].isMainstem = true;
                    whitebox.geospatialfiles.shapefile.Point pointOfInterest
                            = new whitebox.geospatialfiles.shapefile.Point(e.x, e.y);
                    rowData = new Object[2];
                    rowData[0] = new Double(e.nodeID);
                    rowData[1] = "outlet";
                    outputNodes.addRecord(pointOfInterest, rowData);
                }
                outletLinkID = links[e.linkID].outletLinkID;
                // are there any unvisited links connected to this node directly?
                node = nodes.get(endPoints.get(e.endPointID).nodeID);
                for (int epNum2 : node.points) {
                    e2 = endPoints.get(epNum2);
                    if (links[e2.linkID].outlet == -1) { // hasn't previously been encountered
                        links[e2.linkID].outlet = outletID; //.get(e2.linkID).outlet = outletID;
                        links[e2.linkID].outletLinkID = outletLinkID;
                        links[e2.linkID].numDownstreamNodes = numDownstreamNodes + 1; //.get(e2.linkID).numDownstreamNodes = numDownstreamNodes + 1;
                        links[e2.linkID].distToOutlet = distToOutlet + links[e2.linkID].length; //.get(e2.linkID).distToOutlet = distToOutlet + links.get(e2.linkID).length;
                        links[e2.linkID].addOutflowingLink(link.index); //.get(e2.linkID).addDownstreamLink(link.index);
                        streamQueue.add(e2);
                        e2.outflowingNode = true;
                    }
                }

                // get the upstream end point and add its node's points to the queue
                epNum = link.getOtherEndPoint(e.endPointID);
                node = nodes.get(endPoints.get(epNum).nodeID);
//                if (node.numPoints > 2) { // it's either a bifurcation or a channel head
                for (int epNum2 : node.points) {
                    e2 = endPoints.get(epNum2);
                    if (links[e2.linkID].outlet == -1) { // hasn't previously been encountered
                        links[e2.linkID].outlet = outletID; //.get(e2.linkID).outlet = outletID;
                        links[e2.linkID].outletLinkID = outletLinkID;
                        links[e2.linkID].numDownstreamNodes = numDownstreamNodes + 1; //.get(e2.linkID).numDownstreamNodes = numDownstreamNodes + 1;
                        links[e2.linkID].distToOutlet = distToOutlet + links[e2.linkID].length; //.get(e2.linkID).distToOutlet = distToOutlet + links.get(e2.linkID).length;
                        links[e2.linkID].addOutflowingLink(link.index); //.get(e2.linkID).addDownstreamLink(link.index);
                        streamQueue.add(e2);
                        e2.outflowingNode = true;
                    } else if (links[e2.linkID].outlet == outletID
                            && e2.linkID != e.linkID && e2.outflowingNode) {
                        //!links[link.index].outflowingLinksInclude(e2.linkID)) { // diffluence
                        links[e2.linkID].addOutflowingLink(link.index);

                        whitebox.geospatialfiles.shapefile.Point pointOfInterest
                                = new whitebox.geospatialfiles.shapefile.Point(e2.x, e2.y);
                        rowData = new Object[2];
                        rowData[0] = new Double(e2.nodeID);
                        rowData[1] = "diffluence";
                        outputNodes.addRecord(pointOfInterest, rowData);

                    } else if (links[e2.linkID].outlet != outletID && !links[e2.linkID].isOutletLink) {
                        whitebox.geospatialfiles.shapefile.Point pointOfInterest
                                = new whitebox.geospatialfiles.shapefile.Point(e2.x, e2.y);
                        rowData = new Object[2];
                        rowData[0] = new Double(e2.nodeID);
                        rowData[1] = "joined head";
                        outputNodes.addRecord(pointOfInterest, rowData);
                    }
                }
//                } else if (node.numPoints == 2) { // it's not a proper junction in the network.
//                    int epNum2 = node.points.get(0);
//                    if (epNum2 == epNum) {
//                        epNum2 = node.points.get(1);
//                    }
//                    e2 = endPoints.get(epNum2);
//                    if (links[e2.linkID].outlet == -1) { // hasn't previously been encountered
//                        links[e2.linkID].outlet = outletID;
//                        links[e2.linkID].numDownstreamNodes = numDownstreamNodes;
//                        links[e2.linkID].distToOutlet = distToOutlet + links[e2.linkID].length;
//                        links[e2.linkID].addOutflowingLink(link.index);
//                        e2.outflowingNode = true;
//
//                        epNum = link.getOtherEndPoint(epNum2);
//                        node = nodes.get(endPoints.get(epNum).nodeID);
//
//                        for (int epNum3 : node.points) {
//                            e2 = endPoints.get(epNum3);
//                            if (links[e2.linkID].outlet == -1) { // hasn't previously been encountered
//                                links[e2.linkID].outlet = outletID; //.get(e2.linkID).outlet = outletID;
//                                links[e2.linkID].numDownstreamNodes = numDownstreamNodes + 1; //.get(e2.linkID).numDownstreamNodes = numDownstreamNodes + 1;
//                                links[e2.linkID].distToOutlet = distToOutlet + links[e2.linkID].length; //.get(e2.linkID).distToOutlet = distToOutlet + links.get(e2.linkID).length;
//                                links[e2.linkID].addOutflowingLink(link.index); //.get(e2.linkID).addDownstreamLink(link.index);
//                                streamQueue.add(e2);
//                                e2.outflowingNode = true;
//                            } else if (links[e2.linkID].outlet == outletID
//                                    && e2.linkID != e.linkID && e2.outflowingNode) {
//                                //!links[link.index].outflowingLinksInclude(e2.linkID)) { // diffluence
//                                links[e2.linkID].addOutflowingLink(link.index);
//
//                                whitebox.geospatialfiles.shapefile.Point pointOfInterest
//                                        = new whitebox.geospatialfiles.shapefile.Point(e2.x, e2.y);
//                                rowData = new Object[3];
//                                rowData[0] = 1.0;
//                                rowData[1] = new Double(link.index);
//                                rowData[2] = new Double(e2.linkID);
//                                outputNodes.addRecord(pointOfInterest, rowData);
//
//                            }
//                        }
//                    }
//                }

                progress = (int) (100f * numPopped / endPoints.size());
                if (progress != oldProgress) {
                    updateProgress("Priority flood:", progress);
                    oldProgress = progress;
                    // check to see if the user has requested a cancellation
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                }
            }

            //////////////////////////////////////////////////////////////
            // Calculate the total upstream channel length (TUCL), 
            // Shreve stream orders, and the tributary ID by traversing
            // the graph from headwater channels towards their outlets
            //////////////////////////////////////////////////////////////
            updateProgress("Calculating downstream indices...", 0);

            int[] numInflowingLinks = new int[numLinks];
            for (Link lk : links) {
                if (lk != null) {
                    for (int dsl : lk.outflowingLinks) {
                        numInflowingLinks[dsl]++;
                        links[dsl].addInflowingLink(lk.index);
                    }
                }
            }

            LinkedList<Integer> stack = new LinkedList<>();
            int currentTribNum = 1;
            for (i = 0; i < numLinks; i++) {
                if (numInflowingLinks[i] == 0 && isFeatureMapped[i]) {
                    if (links[i].outlet != -1) {
                        stack.push(i);
                        links[i].shreveOrder = 1;
                        links[i].tribID = currentTribNum;
                        currentTribNum++;
                    }
                }
            }

            while (!stack.isEmpty()) {
                int currentLinkIndex = stack.pop();
                links[currentLinkIndex].tucl += links[currentLinkIndex].length;
                links[currentLinkIndex].maxUpstreamDist += links[currentLinkIndex].length;
                int numOutflows = links[currentLinkIndex].outflowingLinks.size();
                for (int dsl : links[currentLinkIndex].outflowingLinks) {
                    links[dsl].tucl += links[currentLinkIndex].tucl / numOutflows;
                    links[dsl].shreveOrder += links[currentLinkIndex].shreveOrder / numOutflows;
                    if (links[currentLinkIndex].maxUpstreamDist > links[dsl].maxUpstreamDist) {
                        links[dsl].maxUpstreamDist = links[currentLinkIndex].maxUpstreamDist;
                    }
                    numInflowingLinks[dsl]--;
                    if (numInflowingLinks[dsl] == 0) {
                        stack.push(dsl);
                        if (links[dsl].inflowingLinks.size() > 1) {
                            //i = 0;
                            //int largestOrder = 0;
                            //int secondLargestOrder = 0;
                            double largestTUCL = 0;
                            int tribOfLargestTUCL = -1;
                            double furthestHead = 0;
                            int tribOfFurthestHead = -1;
                            for (int usl : links[dsl].inflowingLinks) {
                                //i += links[usl].shreveOrder;
//                                if (links[usl].strahlerOrder >= largestOrder) {
//                                    secondLargestOrder = largestOrder;
//                                    largestOrder = links[usl].strahlerOrder;
//                                }
                                if (links[usl].tucl > largestTUCL) {
                                    largestTUCL = links[usl].tucl;
                                    tribOfLargestTUCL = links[usl].tribID;
                                }
                                if (links[usl].maxUpstreamDist > furthestHead) {
                                    furthestHead = links[usl].maxUpstreamDist;
                                    tribOfFurthestHead = links[usl].tribID;
                                }
                            }
//                            if (largestOrder == secondLargestOrder) {
//                                links[dsl].strahlerOrder = largestOrder + 1;
//                            } else {
//                                links[dsl].strahlerOrder = largestOrder;
//                            }
                            //links[dsl].shreveOrder = i;
                            links[dsl].tribID = tribOfFurthestHead; //tribOfLargestTUCL;
                        } else if (links[dsl].inflowingLinks.size() == 1) {
//                            links[dsl].strahlerOrder = links[currentLinkIndex].strahlerOrder;
                            //links[dsl].shreveOrder = links[currentLinkIndex].shreveOrder;
                            links[dsl].tribID = links[currentLinkIndex].tribID;
                        }
                    }
                }
            }

            ///////////////////////////////////////////////////////////
            // Descend from channel heads to outlets a second time to 
            // calculate the Strahler order, and to ID the main stem.
            ///////////////////////////////////////////////////////////
            numInflowingLinks = new int[numLinks];
            for (Link lk : links) {
                if (lk != null) {
                    for (int dsl : lk.outflowingLinks) {
                        numInflowingLinks[dsl]++;
                    }
                }
            }

            stack = new LinkedList<>();
            for (i = 0; i < numLinks; i++) {
                if (numInflowingLinks[i] == 0 && isFeatureMapped[i]) {
                    stack.push(i);
                    links[i].strahlerOrder = 1;
                    //links[i].shreveOrder = 1;
                }
            }

            while (!stack.isEmpty()) {
                int currentLinkIndex = stack.pop();
                if (links[currentLinkIndex].outlet != -1) {
                    // if the tribID of the outlet of this link is the same as the tribID of the link, it's a mainstem link.
                    if (links[links[currentLinkIndex].outletLinkID].tribID == links[currentLinkIndex].tribID) {
                        links[currentLinkIndex].isMainstem = true;
                    }
                }
                for (int dsl : links[currentLinkIndex].outflowingLinks) {
                    numInflowingLinks[dsl]--;
                    if (numInflowingLinks[dsl] == 0) {
                        stack.push(dsl);
                        if (links[dsl].inflowingLinks.size() > 1) {
                            i = 0;
                            int largestOrder = 0;
                            int tribIDLargestOrder = -1;
                            int secondLargestOrder = 0;
                            int tribIDSecondLargestOrder = -1;

                            for (int usl : links[dsl].inflowingLinks) {
                                if (links[usl].strahlerOrder >= largestOrder) {
                                    secondLargestOrder = largestOrder;
                                    tribIDSecondLargestOrder = tribIDLargestOrder;
                                    largestOrder = links[usl].strahlerOrder;
                                    tribIDLargestOrder = links[usl].tribID;
                                }
                            }
                            if (largestOrder == secondLargestOrder && tribIDLargestOrder != tribIDSecondLargestOrder) {
                                links[dsl].strahlerOrder = largestOrder + 1;
                            } else {
                                links[dsl].strahlerOrder = largestOrder;
                            }
                        } else if (links[dsl].inflowingLinks.size() == 1) {
                            links[dsl].strahlerOrder = links[currentLinkIndex].strahlerOrder;
                        }
                    }
                }
            }

            ////////////////////////////////////////////////////////////////////
            // Traverse the graph upstream from outlets to their channel heads
            // to calculate the Horton and Hack stream orders.
            ////////////////////////////////////////////////////////////////////
            updateProgress("Calculating upstream indices...", 0);
            stack = new LinkedList<>();
            boolean[] visited = new boolean[numLinks];
            for (i = 0; i < numLinks; i++) {
                if (links[i] != null && links[i].isOutletLink) {
                    stack.push(i);
                    links[i].hortonOrder = links[i].strahlerOrder;
                    links[i].hackOrder = 1;
                    visited[i] = true;
                }
            }

            int currentHorton, currentHack, currentTrib;
            while (!stack.isEmpty()) {
                int currentLinkIndex = stack.pop();
                currentHorton = links[currentLinkIndex].hortonOrder;
                currentHack = links[currentLinkIndex].hackOrder;
                currentTrib = links[currentLinkIndex].tribID;

                // Visit each the inflowing links to this link.
                for (int usl : links[currentLinkIndex].inflowingLinks) {
                    if (!visited[usl]) {
                        if (links[usl].tribID == currentTrib) {
                            links[usl].hortonOrder = currentHorton;
                            links[usl].hackOrder = currentHack;
                        } else {
                            links[usl].hortonOrder = links[usl].strahlerOrder;
                            links[usl].hackOrder = currentHack + 1;
                        }
                        stack.push(usl);
                        visited[usl] = true;
                    }
                }
            }

            // Outputs
            int[] outParts = {0};
            k = 0;
            PointsList pointsList;
            featureNum = -1;
            oldProgress = -1;
            for (ShapeFileRecord record : input.records) {
                recNum = record.getRecordNumber();
                points = record.getGeometry().getPoints();
                numPoints = points.length;
                partData = record.getGeometry().getParts();
                numParts = partData.length;
                for (part = 0; part < numParts; part++) {
                    featureNum++;
                    if (isFeatureMapped[featureNum]) {
                        startingPointInPart = partData[part];
                        if (part < numParts - 1) {
                            endingPointInPart = partData[part + 1] - 1;
                        } else {
                            endingPointInPart = numPoints - 1;
                        }
                        pointsList = new PointsList();
                        for (i = startingPointInPart; i <= endingPointInPart; i++) {
                            pointsList.addPoint(points[i][0], points[i][1]);
                        }
                        wbGeometry = new whitebox.geospatialfiles.shapefile.PolyLine(outParts, pointsList.getPointsArray());
                        rowData = new Object[13];
                        rowData[0] = new Double(k);
                        link = links[featureNum];
                        rowData[1] = new Double(link.outlet);
                        rowData[2] = link.tucl;
                        rowData[3] = link.maxUpstreamDist;
                        rowData[4] = new Double(link.numDownstreamNodes);
                        rowData[5] = link.distToOutlet;
                        rowData[6] = new Double(link.hortonOrder);
                        rowData[7] = new Double(link.strahlerOrder);
                        rowData[8] = new Double(link.shreveOrder);
                        rowData[9] = new Double(link.hackOrder);
                        if (link.isMainstem) {
                            rowData[10] = 1.0;
                        } else {
                            rowData[10] = 0.0;
                        }
                        rowData[11] = new Double(link.tribID);
                        if (link.outlet != -1) {
                            rowData[12] = 0.0;
                        } else {
                            rowData[12] = 1.0;
                        }
                        output.addRecord(wbGeometry, rowData);
                        k++;
                    }
                }

                progress = (int) (100f * recNum / numFeatures);
                if (progress != oldProgress) {
                    updateProgress("Saving output:", progress);
                    oldProgress = progress;
                    // check to see if the user has requested a cancellation
                    if (cancelOp) {
                        cancelOperation();
                        return;
                    }
                }
            }

            output.write();
            outputNodes.write();
            dem.close();

            pluginHost.updateProgress("Displaying output vector:", 0);

            String paletteDirectory = pluginHost.getResourcesDirectory() + "palettes" + File.separator;
            VectorLayerInfo vli = new VectorLayerInfo(outputFile, paletteDirectory, 255, -1);
            vli.setPaletteFile(paletteDirectory + "qual.pal");
            vli.setOutlinedWithOneColour(false);
            vli.setFillAttribute("OUTLET");
            vli.setPaletteScaled(false);
            vli.setRecordsColourData();
            pluginHost.returnData(vli);

        } catch (OutOfMemoryError oe) {
            showFeedback("An out-of-memory error has occurred during operation.");
        } catch (Exception e) {
            showFeedback("An error has occurred during operation. See log file for details.");
            logException("Error in " + getDescriptiveName(), e);
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            pluginComplete();
        }
    }

    protected class Node implements Comparable<Node> {

        List<Integer> points = new ArrayList<>();
        int numPoints = 0;

        public void addPoint(int n) {
            points.add(n);
            numPoints++;
        }

        double getX() {
            if (numPoints > 0) {
                return endPoints.get(points.get(0)).x;
            } else {
                return -32768.0;
            }
        }

        double getY() {
            if (numPoints > 0) {
                return endPoints.get(points.get(0)).y;
            } else {
                return -32768.0;
            }
        }

        double getZ() {
            if (numPoints > 0) {
                return endPoints.get(points.get(0)).z;
            } else {
                return -32768.0;
            }
        }

        @Override
        public int compareTo(Node o) {
            if (this.getZ() < o.getZ()) {
                return -1;
            } else if (this.getZ() > o.getZ()) {
                return 1;
            } else {
                return 0;
            }
        }

    }

//    protected class GridCellEndPointList {
//        int row;
//        int col;
//        List<Integer> endpointsIDs = new ArrayList<>();
//        int numEndpoints = 0;
//        
//        public GridCellEndPointList()
//    }
    protected class EndPoint implements Comparable<EndPoint> {

        protected double x;
        protected double y;
        protected double z;
        int linkID = -1;
        int nodeID = -1;
        int endPointID = -1;
        boolean outflowingNode = false;
//        double linkMinZ = 0.0;

        public EndPoint(int endPointIndex, int link, double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.linkID = link;
            this.endPointID = endPointIndex;
        }

        @Override
        public int compareTo(EndPoint o) {
            if (this.z < o.z) {
                return -1;
            } else if (this.z > o.z) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    protected class Link {

        int index = -1;
        int endPoint1 = -1;
        int endPoint2 = -1;
        int outlet = -1;
        int outletLinkID = -1;
        boolean isOutletLink = false;
        int numDownstreamNodes = 0;
        double length = -1.0;
        double distToOutlet = 0;
        double tucl = 0;
        double maxUpstreamDist = 0;
        int hortonOrder = 0;
        int strahlerOrder = 0;
        double shreveOrder = 0;
        int hackOrder = 0;
        boolean isMainstem = false;
        int tribID = -1;
        List<Integer> outflowingLinks = new ArrayList<>();
        List<Integer> inflowingLinks = new ArrayList<>();

        public Link(int index, int endPoint1, int endPoint2, double length) {
            this.index = index;
            this.endPoint1 = endPoint1;
            this.endPoint2 = endPoint2;
            this.length = length;
            this.distToOutlet = length;
        }

        public boolean outflowingLinksInclude(int value) {
            return outflowingLinks.contains(value);
        }

        public void addOutflowingLink(int value) {
            outflowingLinks.add(value);
        }

        public void addInflowingLink(int value) {
            inflowingLinks.add(value);
        }

        public int getOtherEndPoint(int thisValue) {
            if (endPoint1 == thisValue) {
                return endPoint2;
            } else {
                return endPoint1;
            }
        }
    }

//    class GridCell implements Comparable<GridCell> {
//
//        public int row;
//        public int col;
//        public double z;
//
//        public GridCell(int row, int col, double z) {
//            this.row = row;
//            this.col = col;
//            this.z = z;
//        }
//
//        @Override
//        public int compareTo(GridCell other) {
//            if (this.z > other.z) {
//                return 1;
//            } else if (this.z < other.z) {
//                return -1;
//            } else if (this.row > other.row) {
//                return 1;
//            } else if (this.row < other.row) {
//                return -1;
//            } else {
//                if (this.col > other.col) {
//                    return 1;
//                } else if (this.col < other.col) {
//                    return -1;
//                }
//                return 0;
//            }
//        }
//    }
//    private static void doSomething(String[] args) {
//        double[] times = new double[10];
//        
//        for (int i = 0; i < 10; i++) {
//            VectorStreamNetworkAnalysis obj = new VectorStreamNetworkAnalysis();
//            obj.setArgs(args);
//            long tStart = System.currentTimeMillis();
//            obj.run();
//            long tEnd = System.currentTimeMillis();
//            long tDelta = tEnd - tStart;
//            times[i] = tDelta / 1000.0;
//        }
//        Arrays.sort(times);
//        for (int i = 0; i < 10; i++) {
//            System.out.println(times[i]);
//        }
//    }
//    
    /**
     * This method is only used for debugging the tool.
     *
     * @param args
     */
    public static void main(String[] args) {
        VectorStreamNetworkAnalysis obj = new VectorStreamNetworkAnalysis();
        args = new String[5];
        // projected coordinate system data
        //args[0] = "/Users/johnlindsay/Documents/Data/NewBrunswick/streams_geog_coord.shp";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/alosDEM1_trim.dep";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/alosDEM1_trim_trim.dep";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/AW3D30_erased.dep";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/AW3D30.dep";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/SRTM-3_trim.dep";
        //args[1] = "/Users/johnlindsay/Documents/Data/NewBrunswick/GMTED2010_trim.dep";
        //args[2] = "not specified";
        //args[3] = "/Users/johnlindsay/Documents/Data/NewBrunswick/AW3D30_streams.shp";

        args[0] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/streams.shp";
        args[1] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/ON/AW3D30.dep";
        //args[1] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/ON/SRTM3_erased.dep";
////        //args[1] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/DEM_erased.dep";
        //args[1] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/ON/GMTED_DEM.dep";
        args[2] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/waterbodies.shp";
        //args[2] = "not specified";
        args[3] = "/Users/johnlindsay/Documents/Research/VectorStreamNetworkAnalysis/data/ON/AW3D30_streams.shp";
        args[4] = "10.0";

        obj.setArgs(args);
        obj.run();
//        doSomething(args);
    }

}
