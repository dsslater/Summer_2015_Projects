/*Uses the error code to determine the type of error message to give*/
function errorHandler(resp) {
	var start = resp.indexOf("(");
	var end = resp.indexOf(")");
	var code = resp.substring(start + 1, end);
	switch(parseInt(code)) {
		case 1:
		case 8:
		case 11:
		case 13:
		case 14:
		case 15:
		case 21:
			alert("Something has gone wrong. Please check the last information you put into the system and RESUBMIT it. If this error persists please contact NAME OF COMPANY for support. Code: " + code);
			break;
		default:
			alert("Something has gone wrong. Please RETRY the the last action you made the system. If this error persists please contact NAME OF COMPANY for support. Code: " + code);
	}
}

/*Takes the name of a file and uses dowload.php in the downloads folder to get the file*/
function download(filename, text) {
	var fileArr = text.split(",");
	for(var i = 0; i < 1; i++) { //only downloads first file which should be the zip
		var element = document.createElement('a');
		element.setAttribute('href',"DOWNLOAD URL" + fileArr[i]);
		element.setAttribute('download', filename);
		element.style.display ='none';
		document.body.appendChild(element);
		element.click();
		document.body.removeChild(element);
	}
}

/*Used for auto populating the effective date field with the current date*/
function addDate() {
	var d = new Date();
	document.getElementById('effective_date').value = (d.getMonth() + 1) + "/" + d.getDate() + "/" + d.getFullYear();
}

/*Uses the template column names, the names in the users csv, and the number provided for mapping to create the two strings for the MYSQL Query to map*/
function mapColumns() {
	var insertOrder = ",`name`";
	var columnOrder = ",`name`";
	var headerArr = document.getElementById("insert").value.split(",");
	var code = document.getElementById("code").value;
	var country = document.getElementById("country").value;
	var min_time= document.getElementById("min_time").value;
	var interval_time= document.getElementById("interval_time").value;
	var seconds= document.getElementById("seconds").value;
	var intra_rate= document.getElementById("intra_rate").value;
	var inter_rate= document.getElementById("inter_rate").value;
	var indeterminate_rate= document.getElementById("indeterminate_rate").value;
	if(code != "") {
		insertOrder += ",`code`";
		columnOrder += (",`" + headerArr[parseInt(code)+1] + "`");
	}
	if(country != "") {
		insertOrder += ",`country`";
		columnOrder += (",`" + headerArr[parseInt(country)+1] + "`");
	}
	if(min_time != "") {
		insertOrder += ",`min_time`";
		columnOrder += (",`" + headerArr[parseInt(min_time)+1] + "`");
	}
	if(interval_time != "") {
		insertOrder += ",`interval_time`";
		columnOrder += (",`" + headerArr[parseInt(interval_time)+1] + "`");
	}
	if(seconds != "") {
		insertOrder += ",`seconds`";
		columnOrder += (",`" + headerArr[parseInt(seconds)+1] + "`");
	}
	if(intra_rate != "") {
		insertOrder += ",`intra_rate`";
		columnOrder += (",`" + headerArr[parseInt(intra_rate)+1] + "`");
	}
	if(inter_rate != "") {
		insertOrder += ",`inter_rate`";
		columnOrder += (",`" + headerArr[parseInt(inter_rate)+1] + "`");
	}
	if(indeterminate_rate != "") {
		insertOrder += ",`indeterminate_rate`";
		columnOrder += (",`" + headerArr[parseInt(indeterminate_rate)+1] + "`");
	}
	document.getElementById("used").value = insertOrder.substring(1);
	document.getElementById("columns").value = columnOrder.substring(1);
}

/*takes the server response to build a mock csv so the user can map the columns*/
function buildTable(respArr) {
	var headerArr = respArr[1].split(",");
	document.getElementById("insert").value = headerArr;
	var table = document.getElementById('csvDisplay');
	table.innerHTML = "";
	var trHead = document.createElement('tr');
	for (var i = 1; i < headerArr.length; i++) {
		var th = document.createElement('th');
		th.style.border = '1px solid black';
		th.style.padding = '5px';
		th.appendChild(document.createTextNode(headerArr[i] + "(" + (i-1)+ ")"));
		trHead.appendChild(th)
	}
	table.appendChild(trHead);
	
	for(var i = 2; i < respArr.length; i++) {
		var lineArr = respArr[i].split(",");
		var tr = document.createElement('tr');
		for (var j = 1; j < lineArr.length; j++) {
			var td = document.createElement('td');
			td.style.border = '1px solid black';
			td.style.padding = '5px';
			td.appendChild(document.createTextNode(lineArr[j]));
			tr.appendChild(td)
		}
		table.appendChild(tr);
	}
}

/*All the animation assignments to hidden buttons that allow for the website to easily slide between different cards and the help bubbles that guide users*/
function animations() {
	helpBubbles();
	sliders();
}

/*Block enter key b/c users will screw it up. Essentially the vendor page has two different submit buttons you could be trying to press, so rather then cause eternal frustration by letting users accidentally double submit a vendor, I have simply disabled the enter key.*/
function keys() {
	$(document).on("keypress", function (e) {
		var code = e.keyCode || e.which;
		if (code == 13) {
			e.preventDefault();
			return false;
		}
	});
}

/*Uses powertips to add helpful note bubbles throughout the RTC process*/
function helpBubbles() {
	$('#csv_chunk').data('powertip', "<b>Vendors:</b> Select the vendors you want and upload them here. Then map the fields that your csv <br>has to expected fields in the database. You can upload as many vendors as you like, one at a time.");
	$('#csv_chunk').powerTip({placement:'s'});
	$('#vendorName_chunk').data('powertip', "<b>Vendors:</b> Make sure this name matches the one used in rules sheets.");
	$('#vendorName_chunk').powerTip({placement:'s'});
	$('#customer_chunk').data('powertip', "<b>Vendors:</b> If this is checked an additional rate table will be created excluding this vendor <br>so that the rates can also be offered to the vendor while avoid circular connections. <br><b>WARNING: Every vendor that is also a customer will increae the run time of this <br>program by around 80%</b>.");
	$('#customer_chunk').powerTip({placement:'s'});
	$('#submit1_chunk').data('powertip', "<b>Vendors:</b> Please click this to submit the current vendor.");
	$('#submit1_chunk').powerTip({placement:'s'});
	$('#secondDoubleNext').data('powertip', "When you are done uploading your vendors click 'Next' to add additional information.");
	$('#secondDoubleNext').powerTip({placement:'n'});
	$('#csvDisplay').data('powertip', "<b>Mapping:</b> If your csv has the 'code_name' in the first column and the 'code' in the second <br>then you would put '0' in for 'code_name' and '1' in for 'code.' If your csv does not have any <br>expected field leave the field blank");
	$('#csvDisplay').powerTip({placement:'s'});
	$('#costRuleDiv').data('powertip', "<b>Cost Rule:</b> How you want your blended costs to be determined. For example '0.7,0.2,0.1' <br><b>WARNING: The more vendors that are included in the blend the longer compiling <br>the rate table will take</b>");
	$('#costRuleDiv').powerTip({placement:'s'});
	$('#pricingRulesDiv').data('powertip', "<b>Pricing Rules:</b> A csv file that contains rules for certains costs. The file should be in the <br>format: Min,Max,(+/*),# . In order from smallest to greatest. The last line should be the default. <br>To increase by 5%, the line should read: (#,#,*,1.05). <b>WARNING: Your largest bracket should <br>have a max value greater than any realistic cost. This is to assure that every code gets <br>marked-up and added to the final table</b>");
	$('#pricingRulesDiv').powerTip({placement:'s'});
	$('#blackListDiv').data('powertip', "<b>BlackList:</b> A csv file that contains a list of vendors and NPANXX regions that they are not <br>permitted to be used in. Should be in the format: NPANXX, Vendor name");
	$('#blackListDiv').powerTip({placement:'s'});
	$('#dropDeltaDiv').data('powertip', "<b>DropDelta:</b> the highest percent increase in price between vendors before the lower priced <br>vendors are discarded given their high chance of failure");
	$('#dropDeltaDiv').powerTip({placement:'s'});
	$('#internalSheetDiv').data('powertip', "<b>Return Internal:</b> Download will include an internal csv that includes the vendors used to <br>create the cost and the rule that was applied to create the final price");
	$('#internalSheetDiv').powerTip({placement:'s'});
	$('#submit3').data('powertip', "When you have finished uploading your vendor<br>files and changed any other optional setting<br>click 'Create Tables' to build your table.");
	$('#submit3').powerTip({placement:'s'});
	$('#fourthBack').data('powertip', "Did you forget a vendor?");
	$('#fourthBack').powerTip({placement:'s'});
}

/*Animations for the cards*/
function sliders() {
	$('#firstNext').click(function () {
		$("#box1").animate({
			left: '-50%'
		}, 500, function () {
			$("#box1").css('left', '150%');
		});
		$("#box1").next().animate({
			left: '32%'
		}, 500);
	});
	$('#secondNext').click(function () {
		$("#box2").animate({
			left: '-50%'
		}, 500, function () {
			$("#box2").css('left', '150%');
		});
		$("#box2").next().animate({
			left: '32%'
		}, 500);
	});
	$('#secondDoubleNext').click(function () {
		$("#box2").animate({
			left: '-50%'
		}, 500, function () {
			$("#box2").css('left', '150%');
		});
		$("#box4").animate({
			left: '32%'
		}, 500);
	});
	$('#thirdNext').click(function () {
		$("#box3").animate({
			left: '-50%'
		}, 500, function () {
			$("#box3").css('left', '150%');
		});
		$("#box3").next().animate({
			left: '32%'
		}, 500);
	});
	$('#fourthNext').click(function () {
		$("#box4").animate({
			left: '-50%'
		}, 500, function () {
			$("#box4").css('left', '150%');
		});
		$("#box4").next().animate({
			left: '32%'
		}, 500);
	});
	$('#fifthNext').click(function () {
		$("#box5").animate({
			left: '-50%'
		}, 500, function () {
			$("#box5").css('left', '150%');
		});
		$("#box1").animate({
			left: '32%'
		}, 500);
	});
	$('#thirdBack').click(function () {
		$("#box3").animate({
			left: '150%'
		}, 500, function () {
			$("#box3").css('left', '150%');
		});
		$("#box3").prev().css('left','0%');
		$("#box3").prev().css('right','150%');
		$("#box3").prev().animate({
			left: '32%'
		}, 500);
	});
	$('#fourthBack').click(function () {

		$("#box4").animate({
			left: '150%'
		}, 500, function () {
			$("#box4").css('left', '150%');
		});
		$("#box4").prev().prev().css('left','0%');
		$("#box4").prev().prev().css('right','150%');
		$("#box4").prev().prev().animate({
			left: '32%'
		}, 500);
	});
	$('#fourthStart').click(function () {
		$("#box4").animate({
			left: '150%'
		}, 500, function () {
			$("#box4").css('left', '150%');
		});
		$("#box1").css('left','0%');
		$("#box1").css('right','150%');
		$("#box1").animate({
			left: '32%'
		}, 500);
	});
}
