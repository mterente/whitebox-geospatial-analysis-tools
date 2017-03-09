# Copyright (C) 2017 Dr. John Lindsay <jlindsay@uoguelph.ca>
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
import os
import sys
import java.lang.Exception as JavaException
import math
from sys import platform
from platform import java_ver as jav
import subprocess
import time
from threading import Thread
from whitebox.ui.plugin_dialog import ScriptDialog
from java.awt.event import ActionListener
from whitebox.geospatialfiles import WhiteboxRaster
from whitebox.geospatialfiles.WhiteboxRasterBase import DataType

name = "RootMeanSquareError" 
descriptiveName = "Root Mean Square Error" 
description = "Calculates the RMSE and other accuracy statistics" 
toolboxes = ["StatisticalTools"] 
	
class PluginTool(ActionListener):
	def __init__(self, args):
		if len(args) != 0:
			self.execute(args)
		else:
			''' Create a dialog for this tool to collect user-specified
			   tool parameters.''' 
			self.sd = ScriptDialog(pluginHost, descriptiveName, self)	
			
			''' Specifying the help file will display the html help
			// file in the help pane. This file should be be located 
			// in the help directory and have the same name as the 
			// class, with an html extension.'''
			self.sd.setHelpFile(name)
	
			''' Specifying the source file allows the 'view code' 
			// button on the tool dialog to be displayed.'''
			self.sd.setSourceFile(os.path.abspath(__file__))
	
			# add some components to the dialog '''
			self.sd.addDialogFile("Input raster file 1 (base)", "Input Base Raster File:", "open", "Raster Files (*.dep), DEP", True, False)
			self.sd.addDialogFile("Input raster file 2 (comparison)", "Input Comparison Raster File:", "open", "Raster Files (*.dep), DEP", True, False)
			
			# Resize the dialog to the standard size and display it '''
			self.sd.setSize(800, 400)
			self.sd.visible = True

	def actionPerformed(self, event):
		if event.getActionCommand() == "ok":
			args = self.sd.collectParameters()
			t = Thread(target=lambda: self.execute(args))
			t.start()

	''' The execute function is the main part of the tool, where the actual
        work is completed.'''
	def execute(self, args):
		try:
			if len(args) != 2:
				pluginHost.showFeedback("Incorrect number of arguments given to tool.")
				return

			# read the input parameters
			inputfile1 = args[0]
			inputfile2 = args[1]
			
			inputraster1 = WhiteboxRaster(inputfile1, 'r')
			rows = inputraster1.getNumberRows()
			cols = inputraster1.getNumberColumns()
			nodata1 = inputraster1.getNoDataValue()

			inputraster2 = WhiteboxRaster(inputfile2, 'r')
			if rows != inputraster2.getNumberRows() or cols != inputraster2.getNumberColumns():
				pluginHost.showFeedback("The input images must have the same number of rows and columns")
				
			nodata2 = inputraster2.getNoDataValue()

			sumSquares = 0.0
			meanVerticalError = 0.0
			z1 = 0.0
			z2 = 0.0
			N = 0.0
			progress = -1.0
			oldprogress = -1.0
			for row in xrange(0, rows):
				for col in xrange(0, cols):
					z1 = inputraster1.getValue(row, col)
					z2 = inputraster2.getValue(row, col)
					if z1 != nodata1 and z2 != nodata2:
						sumSquares += (z2 - z1) * (z2 - z1)
						meanVerticalError += z2 - z1
						N += 1.0
				progress = (int)(100.0 * row / (rows - 1))
				if progress > oldprogress:
					oldprogress = progress
					pluginHost.updateProgress(progress)
				if pluginHost.isRequestForOperationCancelSet():
					pluginHost.showFeedback("Operation cancelled")
					return


			rmse = math.sqrt(sumSquares / N)
			meanVerticalError = meanVerticalError / N
			s = "Vertical Accuracy Analysis\n\n"
			s += "Base File: {}\n".format(inputfile1)
			s += "Comparison File: {}\n\n".format(inputfile2)
			s += "Mean vertical error: {0:.3f}\n".format(meanVerticalError)
			s += "RMSE: {0:.3f}\n".format(rmse)
			s += "Accuracy at 95% confidence limit (m): {0:.3f}\n".format(rmse * 1.96)
			
			# display the output calculation
			pluginHost.returnData(s)
			
		except Exception, e:
			print e
			je = JavaException(str(e))
			pluginHost.logException("Error in {}".format(descriptiveName), je)
			pluginHost.showFeedback("An error has occurred in {} during operation. See log file for details.". format(descriptiveName))
			return
		finally:
			# reset the progress bar
			pluginHost.updateProgress("Progress", 0)
			
if args is None:
	pluginHost.showFeedback("The arguments array has not been set.")
else:
	PluginTool(args)
