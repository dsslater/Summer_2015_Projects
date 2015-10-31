For diagnosing errors, look at 'ErrorMapping.csv'. For a more in depth explanation of how the logic behind RTC works, read 'Rate Table Creator Documentation.pdf'.

The RTC process can be broken up into three main categories that each have a front end and back end implementation

Vendor Form
	Front End:
		The form is found in the top third of the HTML section of containers.html, more specifically it is contained in the second box. It is controlled by the first submission script in containers.html. It is also affected by helpBubbles() and sliders() in js-functions.js. Finally this form processes the server response with buildTable(resp) found in js-functions.js and stores the table name in the mapping form.
	Back End:
		This form is submitted to vendorSubmission.php. Here the input is sanitized and then the first 4 lines are manually processed. This allows the columns to be determined so the table to be created and for the first 3 rows of data to be sent back to the front end to be displayed to the client. Then the csv file is read by load infile and populates the table created. Throws errors 1-6.

Mapping Form
	Front End:
		The form is found in the second third of the HTML section of containers.html, more specifically it is contained in the third box. It is controlled by the second submission script in containers.html. It is also affected by helpBubbles() and sliders() in js-functions.js. This form uses mapColumns() to prep the form before pushing it to the server. Using the last response and the mapping data in the form the function create the ordered list of old and new columns to map. Finally this form processes the server and stores the table name and possible the vendor name in the rules form.
	Beck End:
		This form is submitted to mapping.php. Again, the input is sanitized and then a new table is generated with the mapped data and the old one is dropped. Throws errors 8-12.

Rules form
	Front End:
		The form is found in the last third of the HTML section of containers.html, more specifically it is contained in the fourth box. It is controlled by the last submission script in containers.html It is also affected by helpBubbles() and sliders() in js-functions.js. The form handles the servers response by downloading the zip file it points to using download() found in js-functions.js. Upon success all hidden fields are cleared out so that a new RT can be created.
	Back End:
		This form is submitted to generate.php This is the most complex server side script. The first half ogf generate.php is sanitizing input and creating names. Essentially this is collecting all the information necessary for the execution process. then after the 'START OF EXECUTION' banner the rest of the script called functions in g-functions.php. The script starts by generating the optimal table:
			1) 	Combine all of the vendor tablew to create one giant table of products
			2)	If a black list was provided then those products are removed from the combined list
			3)	For each unique NPANXX take all the vendors that support it and sort them by cost for 
				the three different costs.
			4)	The calc_table is then created with dynamically generated columns to fit the blend 
				rate provided
			5)	Depending on whether or not a dropDelta is provided the sorted vendors are then 
				processed to choose the final set that are then put into the calc table. This 
				eliminates extra vendors and also generates averages to fill any holes.
			6)	Then a cost table is created which takes the columns of the calc table and blends 
				them together according to the blend rule. This gives the final cost.
			7)	Next, to create the final table, the pricing rules are used to query the cost table 
				and all costs that fall within the given range have the provided rule applied to them.
			8)	This final table is written out to a csv and the file is recorded so that it can 
				later be zipped
			9)	If the internal table is requested then information from the cost table, final table, 
				and the pricing rules is gathered up and added to the calc table. This new 'internal'
				table is then written out to a csv and the file is recorded.
			10)	Now for all vendors that are customers steps 4-8 are redone with step five also 
				removing the vendor that is currently be processed as the customer from the sets.
			11)	All remaining tables are dropped to 'clean up'.
			12)	All the files are zipped and the name of the zip file is echoed out to the client so 
				that the zip can be downloaded.
