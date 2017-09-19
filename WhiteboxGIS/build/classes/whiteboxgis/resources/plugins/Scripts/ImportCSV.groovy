/*
 * Copyright (C) 2014 Dr. John Lindsay <jlindsay@uoguelph.ca>
 * Tool modified Feb. 2017
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
 
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.io.File
import java.util.Date
import java.util.ArrayList
import java.util.Arrays
import whitebox.interfaces.WhiteboxPluginHost
import whitebox.geospatialfiles.ShapeFile
import whitebox.geospatialfiles.shapefile.*
import whitebox.geospatialfiles.shapefile.attributes.*
import whitebox.ui.plugin_dialog.ScriptDialog
import whitebox.utilities.FileUtilities;
import whitebox.utilities.Topology;
import whitebox.interfaces.InteropPlugin.InteropPluginType
import groovy.transform.CompileStatic

// The following four variables are required for this 
// script to be integrated into the tool tree panel. 
// Comment them out if you want to remove the script.
def name = "ImportCSV"
def descriptiveName = "Import CSV"
def description = "Imports a comma-separated values text file (*.csv) to a vector points shapefile."
def toolboxes = ["IOTools"]

// The following variables tell the plugin host that 
// this tool should be used as a supported geospatial
// data format.
def extensions = ["csv"]
def fileTypeName = "Comma-separated Values"
def isRasterFormat = false
def interopPluginType = InteropPluginType.importPlugin;

public class ImportCSV implements ActionListener {
    private WhiteboxPluginHost pluginHost
    private ScriptDialog sd;
    private String descriptiveName
	
    public ImportCSV(WhiteboxPluginHost pluginHost, 
        String[] args, def name, def descriptiveName) {
        this.pluginHost = pluginHost
        this.descriptiveName = descriptiveName
			
        if (args.length > 0) {
            execute(args)
        } else {
            // Create a dialog for this tool to collect user-specified
            // tool parameters.
            sd = new ScriptDialog(pluginHost, descriptiveName, this)	
		
            // Specifying the help file will display the html help
            // file in the help pane. This file should be be located 
            // in the help directory and have the same name as the 
            // class, with an html extension.
            sd.setHelpFile(name)
		
            // Specifying the source file allows the 'view code' 
            // button on the tool dialog to be displayed.
            def pathSep = File.separator
            def scriptFile = pluginHost.getResourcesDirectory() + "plugins" + pathSep + "Scripts" + pathSep + name + ".groovy"
            sd.setSourceFile(scriptFile)
			
            // add some components to the dialog
            sd.addDialogMultiFile("Select some input CSV files", "Input CSV Files:", "Comma-separated Values Files (*.csv, *.txt), CSV, TXT")
            sd.addDialogDataInput("x-coordinate column number (optional)", "x-Coordinate Column Number (optional):", "", true, true)
            sd.addDialogDataInput("y-coordinate column number (optional)", "y-Coordinate Column Number (optional):", "", true, true)
            
//			sd.addDialogComboBox("First column definition", "<html>1<sup>st</sup> column definition:</html>", ["x-coordinate", "y-coordinate", "numerical attribute", "string attribute"], 0)
//            sd.addDialogComboBox("Second column definition", "<html>2<sup>nd</sup> column definition:</html>", ["x-coordinate", "y-coordinate", "numerical attribute", "string attribute"], 1)
//            sd.addDialogComboBox("Third column definition", "<html>3<sup>rd</sup> column definition:</html>", ["x-coordinate", "y-coordinate", "numerical attribute", "string attribute", "not present"], 4)
//            sd.addDialogComboBox("Fourth column definition", "<html>4<sup>th</sup> column definition:</html>", ["x-coordinate", "y-coordinate", "numerical attribute", "string attribute", "not present"], 4)
//            sd.addDialogComboBox("Fifth column definition", "<html>5<sup>th</sup> column definition:</html>", ["x-coordinate", "y-coordinate", "numerical attribute", "string attribute", "not present"], 4)
            
            // resize the dialog to the standard size and display it
            sd.setSize(800, 400)
            sd.visible = true
        }
    }

    // The CompileStatic annotation can be used to significantly
    // improve the performance of a Groovy script to nearly 
    // that of native Java code.
    @CompileStatic
    private void execute(String[] args) {
        try {
			String csvFileName = null;
        	String shapefileName = null;
        	int i = 0;
        	double x, y
        	int progress = 0;
        	int oldProgress = -1;
        	String delimeter = ",";

        	if (args.length < 1) {
				pluginHost.showFeedback("Incorrect number of arguments given to tool.")
				return
			}
			// read the input parameters
			String inputFileString = args[0]
			if (inputFileString.isEmpty()) {
	            pluginHost.showFeedback("One or more of the input parameters have not been set properly.");
	            return;
	        }
			String[] inputFiles = inputFileString.split(";")
			int numFiles = inputFiles.length;

			int xColNum = -1
			if (args.length >=3 && args[1].toLowerCase() != "not specified") {
				xColNum = Integer.parseInt(args[1]) - 1;
			}
			int yColNum = -1
			if (args.length >=3 && args[2].toLowerCase() != "not specified") {
				yColNum = Integer.parseInt(args[2]) - 1;
			}
			int numAttributes = 0

			// try to find the x and y and figure out the attributes
			csvFileName = inputFiles[0];
            if (!((new File(csvFileName)).exists())) {
                pluginHost.showFeedback("CSV file does not exist.");
                return;
            }
			
            String[] fileLines = ((new File(csvFileName)).text).split("\n")
            if (fileLines.length == 1) {
            	fileLines = this.parseEOL((new File(csvFileName)).text)
            }
            String[] secondLine
            String[] firstLine
            
//            if (fileLines.length > 1) {
            	secondLine = fileLines[1].split(delimeter)
            	if (secondLine.length == 1) {
            		delimeter = "\t";
            		secondLine = fileLines[1].split(delimeter)
            		if (secondLine.length == 1) {
            			delimeter = ";";
            			secondLine = fileLines[1].split(delimeter)
            			if (secondLine.length == 1) {
            				delimeter = " ";
            				secondLine = fileLines[1].split(delimeter)
            			}
            		}
            	}

            	firstLine = fileLines[0].split(delimeter)
//            	pluginHost.showFeedback("{firstLine.length}")
//            } else {
//            	secondLine = fileLines[0].split(delimeter) // actually the first line
//            	if (secondLine.length == 1) {
//            		delimeter = "\t";
//            		secondLine = fileLines[0].split(delimeter)
//            		if (secondLine.length == 1) {
//            			delimeter = ";";
//            			secondLine = fileLines[0].split(delimeter)
//            			if (secondLine.length == 1) {
//            				delimeter = " ";
//            				secondLine = fileLines[0].split(delimeter)
//            			}
//            		}
//            	}
//            }

            int numColumnDefinitions = secondLine.length
            def columnDef = new String[numColumnDefinitions]
            for (i = 0; i < numColumnDefinitions; i++) {
            	if (secondLine[i].trim().isInteger()) {
            		columnDef[i] = "numeric"
					numAttributes++
            	} else if (secondLine[i].trim().isDouble()) {
            		if (xColNum == -1) {
            			columnDef[i] = "x"
            			xColNum = i
            		} else if (yColNum == -1) {
            			columnDef[i] = "y"
            			yColNum = i
            		} else {
            			columnDef[i] = "numeric"
						numAttributes++
            		}
            	} else {
            		columnDef[i] = "string"
					numAttributes++
            	}
            }
            fileLines = null
            secondLine = null

			if (xColNum == -1 || yColNum == -1) {
				pluginHost.showFeedback("The x- and y-coordinate columns could not be found.")
				return
			}
			
			int[] attributeColumns = new int[numAttributes]
			int[] attributeTypes = new int[numAttributes]
			
            DBFField[] fields = new DBFField[numAttributes + 1];
            fields[0] = new DBFField();
            fields[0].setName("FID");
            fields[0].setDataType(DBFField.DBFDataType.NUMERIC);
            fields[0].setFieldLength(10);
            fields[0].setDecimalCount(0);

			int j = 1
			for (i = 0; i < numColumnDefinitions; i++) {
				
				if (columnDef[i].equals("numeric")) { 
					fields[j] = new DBFField();
					if (firstLine != null) {
//						pluginHost.showFeedback(firstLine[i])
		            	fields[j].setName(firstLine[i].take(9).toString());
					} else {
						fields[j].setName("ATTRIB_${j}");
					}
		            fields[j].setDataType(DBFField.DBFDataType.NUMERIC);
		            fields[j].setFieldLength(10);
		            fields[j].setDecimalCount(4);
		            attributeTypes[j - 1] = 0
		            attributeColumns[j - 1] = i
		            j++
				} else if (columnDef[i].equals("string")) {
					fields[j] = new DBFField();
					if (firstLine != null) {
//						pluginHost.showFeedback(firstLine[i])
		            	fields[j].setName(firstLine[i].take(9).toString());
					} else {
						fields[j].setName("ATTRIB_${j}");
					}
//		            fields[j].setName("ATTRIB_${j}");
		            fields[j].setDataType(DBFField.DBFDataType.STRING);
		            fields[j].setFieldLength(12);
		            attributeTypes[j - 1] = 1
		            attributeColumns[j - 1] = i
		            j++
				}
			}
            
            for (i = 0; i < numFiles; i++) {
                progress = (int) (100f * i / (numFiles - 1));
                if (numFiles > 1) {
                	pluginHost.updateProgress("Loop " + (i + 1) + " of " + numFiles + ":", progress);
                }
                csvFileName = inputFiles[i];
                if (!((new File(csvFileName)).exists())) {
                    pluginHost.showFeedback("CSV file does not exist.");
                    break;
                }
				
                def csvFile = new File(csvFileName)
                
				// set up the output file
				String outputFile = csvFileName.replace(".csv", ".shp").replace(".CSV", ".shp")
				ShapeFile output = new ShapeFile(outputFile, ShapeType.POINT, fields)

				int featureNum = 1
				boolean checkForHeader = false

				fileLines = ((csvFile).text).split("\n")
	            if (fileLines.length == 1) {
	            	fileLines = this.parseEOL((new File(csvFileName)).text)
	            }
	            for (line in fileLines) {
//				csvFile.eachLine { line -> 
					String str = String.valueOf(line)
					String[] columnData = str.split(delimeter)
					if (columnData.length == 1) {
                		delimeter = "\t";
                		columnData = str.split(delimeter)
                		if (columnData.length == 1) {
                			delimeter = ";";
                			columnData = str.split(delimeter)
                			if (columnData.length == 1) {
                				delimeter = " ";
                				columnData = str.split(delimeter)
                			}
                		}
                	}
     
					int lowerNum = (numColumnDefinitions < columnData.length) ? numColumnDefinitions :  columnData.length
					boolean headerLine = false
					if (!checkForHeader) {
						// see if it is a header line
						headerLine = true
						for (i = 0; i < lowerNum; i++) {
		                	if (columnData[i].trim().isDouble()) {
		                		headerLine = false
		                	}
		                }
		                checkForHeader = true
					}

					if (!headerLine) {
						x = Double.parseDouble(columnData[xColNum].trim())
						y = Double.parseDouble(columnData[yColNum].trim())
	
						// output the point
						whitebox.geospatialfiles.shapefile.Point wbGeometry = 
						  new whitebox.geospatialfiles.shapefile.Point(x, y);                  
	                    Object[] rowData = new Object[numAttributes + 1]
	                    rowData[0] = new Double(featureNum)
	                    for (int k = 0; k < numAttributes; k++) {
	                    	if (k < columnData.length) {
								if (attributeTypes[k] == 0) { // numeric
									if (columnData[attributeColumns[k]].isNumber()) {
										rowData[k + 1] = new Double(columnData[attributeColumns[k]])
									} else {
										rowData[k + 1] = null
									}
								} else { // string
									rowData[k + 1] = columnData[attributeColumns[k]].trim().replaceAll('^"|"$','').replace('?','')
								}
	                    	} else {
	                    		rowData[k + 1] = null
	                    	}
						}
	                    
	                    output.addRecord(wbGeometry, rowData);
	
						featureNum++
					}
				}

				output.write()
				
				pluginHost.returnData(outputFile)
				
            	// check to see if the user has requested a cancellation
				if (pluginHost.isRequestForOperationCancelSet()) {
					pluginHost.showFeedback("Operation cancelled")
					return
				}
                
            }

        } catch (OutOfMemoryError oe) {
            pluginHost.showFeedback("An out-of-memory error has occurred during operation.")
	    } catch (Exception e) {
	    	pluginHost.showFeedback(e.toString())
	        pluginHost.showFeedback("An error has occurred during operation. See log file for details.")
	        pluginHost.logException("Error in " + descriptiveName, e)
        } finally {
        	// reset the progress bar
        	pluginHost.updateProgress(0)
        }
    }

    private String[] parseEOL(String fileText) {
		int numLines = 0;
		for (int i = 0; i < fileText.length(); i++){
		    char c = fileText.charAt(i);
		    if (c == "\r" || c == "\n") {
		    	numLines++;
		    }
		}
			
		String[] retArray = new String[numLines];
		String s = "";
		int lineNum = 0;
		for (int i = 0; i < fileText.length(); i++){
		    char c = fileText.charAt(i);
		    if ((c == "\r" || c == "\n") && s.length() > 0) {
		    	retArray[lineNum] = s
		    	s = "";
		    	lineNum++;
		    } else {
		    	s += c
		    }
		}
		return retArray;
	}

    @Override
    public void actionPerformed(ActionEvent event) {
    	if (event.getActionCommand().equals("ok")) {
            final def args = sd.collectParameters()
            sd.dispose()
            final Runnable r = new Runnable() {
            	@Override
            	public void run() {
                    execute(args)
            	}
            }
            final Thread t = new Thread(r)
            t.start()
    	}
    }
}

if (args == null) {
    pluginHost.showFeedback("Plugin arguments not set.")
} else {
    def f = new ImportCSV(pluginHost, args, name, descriptiveName)
}
