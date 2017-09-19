import sys
import os
import java.lang.Exception as JavaException

'''Deletes the error logs contained within the logs folder '''

parentMenu = "Tools"
menuLabel = "Clear Error Logs"

try:

	log_directory = pluginHost.getLogDirectory()

	files = []
	for file in os.listdir(log_directory):
		if file.endswith(".xml"):
			files.append(os.path.join(log_directory, file))

	for f in files:
		try:
			os.remove(f)
		except:
			print("Could not delete {} because it was in use.". format(f))

	warning_str = """ All log files have now been deleted including the
	active log files. You will not be able to log any  
	additional errors until relaunching Whitebox."""

	pluginHost.showFeedback(warning_str)
		
except BaseException as e:
	print "Unexpected error:", e
	je = JavaException(str(e))
	pluginHost.logException("Error in ViewErrorLogs", je)
	pluginHost.showFeedback("Error: {}".format(str(e)))
