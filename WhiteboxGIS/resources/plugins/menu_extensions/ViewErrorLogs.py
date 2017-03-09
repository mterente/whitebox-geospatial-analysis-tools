import sys
import os
import java.lang.Exception as JavaException

'''Displays the error logs contained within the logs folder '''

parentMenu = "View"
menuLabel = "View Error Logs"

try:

	log_directory = pluginHost.getLogDirectory()
	
	ret_str = ""
	num_files = 0
	for file in os.listdir(log_directory):
		if file.endswith(".xml"):
			num_files += 1
			log_file = os.path.join(log_directory, file)
			ret_str += "File: {}\n".format(log_file)
			with open(log_file) as f:
				content = f.readlines()
				if len(content) > 0:
					for l in content:
						ret_str += l
				else:
					ret_str += "File is empty\n"
					
			ret_str += "\n\n"

	if num_files == 0:
		ret_str = "No log files were located in the logs directory."

	pluginHost.returnData(ret_str)

except BaseException as e:
	print "Unexpected error:", e
	je = JavaException(str(e))
	pluginHost.logException("Error in ViewErrorLogs", je)
	pluginHost.showFeedback("Error: {}".format(str(e)))
