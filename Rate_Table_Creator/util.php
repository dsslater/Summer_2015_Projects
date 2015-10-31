<?php

/*Returns a PDO object for connecting to the MYSQL database*/
function connect($server, $dbname, $username, $password) {
	try {
		$conn = new PDO("mysql:host=".$server.";dbname=".$dbname, $username,$password, array(PDO::MYSQL_ATTR_LOCAL_INFILE => true));
	} catch(PDOException $e) {
		die("ERROR: (1) ");
	}
	return $conn;
}

/*Prepares a PDO statement and returns the object to be executed*/
function prepare_stmt($conn, $query_base, $err_code) {
    if(!$stmt = $conn->prepare($query_base)) {
        die("ERROR: ($err_code) ");
    }
    return $stmt;
}

/*Execute PDo prepared statement*/
function query($conn, $stmt, $params, $err_code) {
    if(!$stmt->execute($params)) {
        die("ERROR: ($err_code) ");
    }
    return $stmt->fetchAll();
}

/* Filter used by the three main scripts for input that is not just limited to numbers */
function char_filter($input) {
	return preg_replace('/[^a-zA-Z0-9_,\.]+/','', $input);
}

/* Filter used by the three main scripts for input that is limited to just numbers */
function num_filter($input) {
	return preg_replace('/[^0-9,\.]+/','', $input);
}

/*  Filter for operators (+, *)  */
function op_filter($input) {
	return preg_replace('/[^+*]+/','', $input);
}
