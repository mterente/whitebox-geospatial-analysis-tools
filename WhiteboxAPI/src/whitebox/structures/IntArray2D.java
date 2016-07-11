/*
 * Copyright (C) 2015 johnlindsay
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
package whitebox.structures;

/**
 * This class is used to create a 2-D array of 32-bit integers.
 * @author John Lindsay
 */
public class IntArray2D {
    private int rows = 0;
    private int columns = 0;
    private int[] data;
    private int numCells = 0;
    private int noData = -32768;
    
    /**
     * Initializes a new IntArray2D with a specified number of rows, columns,
     * and a user-specified NoData value.
     * @param rows The number of rows.
     * @param columns The number of columns.
     * @param noData The user-specified NoData value.
     */
    public IntArray2D(int rows, int columns, int noData) {
        this.rows = rows;
        this.columns = columns;
        this.noData = noData;
        numCells = rows * columns;
        data = new int[numCells];
        for (int i = 0; i < numCells; i++) {
            data[i] = noData;
        }
    }
    
    /**
     * Initializes a new IntArray2D with a specified number of rows and columns.
     * @param rows The number of rows.
     * @param columns The number of columns.
     */
    public IntArray2D(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        numCells = rows * columns;
        data = new int[numCells];
        for (int i = 0; i < numCells; i++) {
            data[i] = noData;
        }
    }
    
    public void incrementValue(int row, int column, int value) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return;
        }
        int cellNum = row * columns + column;
        data[cellNum] += value;
    }
    
    public void incrementValue(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return;
        }
        int cellNum = row * columns + column;
        data[cellNum]++;
    }
    
    public void decrementValue(int row, int column, int value) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return;
        }
        int cellNum = row * columns + column;
        data[cellNum] -= value;
    }
    
    public void decrementValue(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return;
        }
        int cellNum = row * columns + column;
        data[cellNum]--;
    }
    
    public void setValue(int row, int column, int value) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return;
        }
        int cellNum = row * columns + column;
        data[cellNum] = value;
    }
    
    public int getValue(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return noData;
        }
        int cellNum = row * columns + column;
        return data[cellNum];
    }
    
    public int getNoDataValue() {
        return noData;
    }
    
    public static void main(String[] arg) {
        IntArray2D intArray = new IntArray2D(100, 100, -999);
        
        intArray.setValue(5, 4, 4);
        intArray.setValue(5, 4, 8);
        intArray.setValue(5, 5, 12);
        intArray.setValue(6, 76, 9);
        intArray.setValue(-100, 76, 9);
        
        System.out.println(intArray.getValue(5, 4));
        System.out.println(intArray.getValue(5, 5));
        System.out.println(intArray.getValue(6, 76));
        System.out.println(intArray.getValue(-100, 76));
    }
}
