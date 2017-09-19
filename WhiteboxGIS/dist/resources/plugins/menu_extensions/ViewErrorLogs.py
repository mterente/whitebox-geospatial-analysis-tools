import sys
import os
import java.lang.Exception as JavaException

'''Displays the error logs contained within the logs folder '''

parentMenu = "View"
menuLabel = "View Error Logs"

try:

	log_directory = pluginHost.getLogDirectory()

	ret_str = ""
	for file in os.listdir(log_directory):
		if file.endswith(".xml"):
			log_file = os.path.join(log_directory, file)
			ret_str += "File: {}\n\n".format(log_file)
			with open(log_file) as f:
				content = f.readlines()
				for l in content:
					ret_str += l
			ret_str += "\n\n"
				

	pluginHost.returnData(ret_str)

except BaseException as e:
	print "Unexpected error:", e
	je = JavaException(str(e))
	pluginHost.logException("Error in ViewErrorLogs", je)
	pluginHost.showFeedback("Error: {}".format(str(e)))
