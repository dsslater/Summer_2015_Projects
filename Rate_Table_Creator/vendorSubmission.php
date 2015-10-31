<?php
require_once('const.php'); //db credentials and injection filters
require_once('util.php'); //PDO handling functions
//ini_set('display_errors', 'On');
//error_reporting(E_ALL | E_STRICT);
if ($_FILES["csv"] && $_FILES["csv"]["size"] > 0) {
	
	$numReturnLines = 3;
	$conn = connect(SERVER, DBNAME, USERNAME, PASSWORD); //Throws 'ERROR (1)' if it can't connect

	$file = $_FILES["csv"]["tmp_name"];
	$vendorName = char_filter($_POST['vendorName']);
	$handle = fopen($file,"r");
	$table = substr($file, 5);
	$data = fgetcsv($handle, 300, ",");
	$table_q = "";
	$col = count($data); 
	$first = TRUE;
	$lines = "";
	/*Creating strings for the column head and the three lines that will be returned to the client for mapping */
	$table_q .= "`" . char_filter($data[0]) . "`";
	$lines .= "," . char_filter($data[0]);
	$table_q .= " VARCHAR(50)";
	for($i = 1; $i < $col; $i++) {
		$temp = ",`" . char_filter($data[$i]) . "` VARCHAR(50)";
		$lines .= "," . char_filter($data[$i]);
		$table_q .= $temp;
	}
	for($j = 0; $j < $numReturnLines; $j++) {
		$lines .= "|";
		$data = fgetcsv($handle, 300, ",");
		for($i = 0; $i < $col; $i++) {
			$lines .= "," . char_filter($data[$i]);
		}
	}
	
	/*Creates table based off of the first column in the csv providede*/
	if (!$conn->query("CREATE TABLE IF NOT EXISTS $table ($table_q ) ENGINE = MyISAM ;")) {
		die("ERROR: (3) " . $conn->error);
	}
	
	/*Load the file into the table ignoring the first row (headers)*/
	if(!$conn->query("LOAD DATA LOCAL INFILE '$file' INTO TABLE $table FIELDS TERMINATED BY ',' IGNORE 1 LINES;")) {
		die("ERROR: (4) ");
	}

	if(!$conn->query("ALTER TABLE $table ADD name VARCHAR(50);")) {
		die("ERROR: (5) ");
	}

	if(!$conn->query("UPDATE $table SET NAME = '$vendorName'")) {
		die("ERROR: (6) ");
	}
		
	$conn = null;
	echo $table .= "|";
	echo $lines;
} else {
	die("ERROR: (2)");
}
