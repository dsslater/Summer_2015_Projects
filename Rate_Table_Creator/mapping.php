<?php
require_once('const.php'); //db credentials
require_once('util.php'); //PDO handling functions
//ini_set('display_errors', 'On');
//error_reporting(E_ALL | E_STRICT);
$numReturnLines = 3;
$conn = connect(SERVER, DBNAME, USERNAME, PASSWORD); //Throws 'ERROR (1)' if it can't connect
$table = char_filter($_POST['table']);
if(strncmp($table, 'php', 3) == 0) { //prevents injection of malicious table names
	$table_new = $table . "_f";
	$insert = char_filter($_POST['used']);
	$columns = char_filter($_POST['columns']);

	if (!$conn->query("create table $table_new like z_rateTableTemplate")) {
		die("ERROR: (9) ");
	}

	if (!$conn->query("insert into $table_new ($insert) select $columns from $table")) {
		echo "ERROR: (10) " . $conn->error;
		if (!$conn->query("DROP TABLE IF EXISTS $table_new")) { //if something goes wrong then by deleting the
			die("ERROR: (11) "); 								//new table,and dying before the old table is  
		}														//destroyed, the user can resubmit
		die();
	}

	if (!$conn->query("DROP TABLE IF EXISTS $table")) {
		echo("WARNING: (12) ");
	}
	$conn = null;
	echo $table_new;
} else {
	die("ERROR: (8) ");
}
