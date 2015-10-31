<?php

/*Takes all of the individual vendor tables and puts them end to end, then if a black list is provided it removes those products from the list*/
function combineVendors($conn, $masterTable, $columns, $vendorArr, $numtables) {
    if (!$conn->query("CREATE TABLE $masterTable LIKE z_rateTableTemplate")) {
        die("ERROR: (16) ");
    }
	if(!$conn->query("ALTER TABLE $masterTable ADD INDEX(`code`);")) {
        die("ERROR: (17) ");
    }
    for($i = 0; $i < $numtables; $i++) {
		if(strncmp($vendorArr[$i], 'php', 3) == 0) { //prevents injection of malicious table names
			$name = char_filter($vendorArr[$i]);
			if (!$conn->query("INSERT INTO $masterTable ($columns) SELECT $columns FROM $name")) {
				die("ERROR: (18) ");
			}
			if (!$conn->query("DROP TABLE IF EXISTS $name")) {
				echo("WARNING: (19) ");
			}
		}
	}
	
	if (!$conn->query("DELETE FROM $masterTable WHERE `min_time` != 6 OR `interval_time` != 6")) { 
            die("ERROR: (20) ");// all rates must be 6x6 compliant. Doing anything else would be a nightmare. 
        }//Which would you show on the final table? How do you blend? Also the checks will double the runtime...
		
	if($_FILES['blackList'] && $_FILES['blackList']['size'] > 0) { //process black list
        removeVendors($conn, $masterTable);
    }
}

/*Uses the provided black list to remove bad vendors*/
function removeVendors($conn, $masterTable) {
    $file = $_FILES['blackList']['tmp_name'];
    $handle = fopen($file, 'r');
    while($data = fgetcsv($handle, 50, ',')) {
        $regex = $data[0];
        $regex_fix = fix_regex($regex);
        $name = $data[1];
        $name_fix = fix_regex($name);
        if (!$conn->query("DELETE FROM $masterTable WHERE `code` LIKE '$regex_fix' AND `name` LIKE '$name_fix'")) {
            die("ERROR: (21) ");
        }
    }
}

/*Sanitizes the black list data and then turns standard wildcard characters into MYSQL wildcard characters.*/
function fix_regex($regex) {
	$regex = preg_replace('/[^a-zA-Z0-9*?%_]+/','', $regex);
    $reg = array("*", "?");
    $sql = array("%", "_");
    return str_replace($reg, $sql, $regex);
}

/*Using Group Concat, Substring, and Order By this function gets the cheapest $numVendor vendors cost and name for each NPANXX and for each type of cost. This information is then stores them as comma separated lists in columns*/
function createInternalTable($conn, $masterTable, $internal, $numVendors) {
    if (!$conn->query("CREATE TABLE $internal LIKE z_internalRateTableTemplate")) {
        die("ERROR: (22) ");
    }

    if(!$conn->query("INSERT INTO $internal (`code`, `interVendors`, `inter_rates`, `intraVendors`, `intra_rates`, `indeterminateVendors`, `indeterminate_rates`, `country`, `effective_date`, `min_time`, `interval_time`, `seconds`)
	SELECT `code`, SUBSTRING_INDEX(GROUP_CONCAT(`name` ORDER BY `inter_rate` SEPARATOR ','),',',$numVendors), SUBSTRING_INDEX(GROUP_CONCAT(`inter_rate` ORDER BY `inter_rate` SEPARATOR ','),',',$numVendors), SUBSTRING_INDEX(GROUP_CONCAT(`name` ORDER BY `intra_rate` SEPARATOR ','),',',$numVendors), SUBSTRING_INDEX(GROUP_CONCAT(`intra_rate` ORDER BY `intra_rate` SEPARATOR ','),',',$numVendors), SUBSTRING_INDEX(GROUP_CONCAT(`name` ORDER BY `indeterminate_rate` SEPARATOR ','),',',$numVendors), 	SUBSTRING_INDEX(GROUP_CONCAT(`indeterminate_rate` ORDER BY `indeterminate_rate` SEPARATOR ','),',',$numVendors), `country`,`effective_date`,`min_time`,`interval_time`,`seconds` FROM $masterTable GROUP BY `code` ORDER BY `inter_rate`;")) {
        die("ERROR: (23) ");
    }
}

/*Based off of the number of vendors the columns needed are dynamically generated and the table is created. The list of columns used is returned*/
function createCalcTable($conn, $internal, $calc_table, $numCols) {
    $inter_cols = "";
    for($i = 1; $i <= $numCols; $i++) { //create columns based off of the $numCols
        $inter_cols .= ", `inter_vend_$i` VARCHAR(50)";
        $inter_cols .= ", `inter_$i` FLOAT(8,7)";
    }
    $intra_cols = str_replace("inter", "intra", $inter_cols); //then an intra version is created
    $indeterminate_cols = str_replace("inter", "indeterminate", $inter_cols); //and an indeterminate version
    $cols = "$inter_cols $intra_cols $indeterminate_cols"; //they are put together
    if (!$conn->query("CREATE TABLE IF NOT EXISTS $calc_table (`code` INT(11) $cols) ENGINE = MyISAM")) { //and then used to create the table
        die("ERROR: (24) ");
    }
    if (!$conn->query("CREATE INDEX id_index ON $calc_table (code);")) { //adding an index on the code will speed grouping and sorting
        die("ERROR: (25) ");
    }
    return str_replace(" VARCHAR(50)","",str_replace(" FLOAT(8,7)", "", substr($cols, 1))); //returns the full list of columns created ex. (inter_vend_1, inter_1...)
}

/*If a drop delta is used then this function will properly remove any vendors that fail the test and remove any costs from the vendor in $bad_vendor. The final set is then used to the populate the calc table.*/
function proccessVendors($conn, $internal, $calc_table, $temp_file, $dropDelta, $numCols, $cols, $bad_vendor = NULL) {
    if (!$conn->query("SELECT `code`, `interVendors`, `inter_rates`, `intraVendors`, `intra_rates`, `indeterminateVendors`, `indeterminate_rates` FROM $internal INTO OUTFILE '$temp_file' FIELDS TERMINATED BY '|' LINES TERMINATED BY '\n'")) {
        die("ERROR: (26) ");
    } //first the internal table is dumped out to a csv so that the contents can be processed
	$num_qs = count(explode(',', $cols)) + 1;
	$qs = '?';
	for($i = 1; $i < $num_qs; $i++) {
		$qs .= ',?';
	}
	$stmt = prepare_stmt($conn, "INSERT INTO $calc_table (`code`, $cols) VALUES ($qs);", 27);
    $handle = fopen($temp_file, "r");
    while($data = fgetcsv($handle, 0, "|")) { //Then the csv is cycled through
        $output = "";
        $code = $data[0];

        $inter_vends = $data[1]; //the concatenated fields in the table are
        $inter_vends_arr = explode(",",$inter_vends); //broken up into arrays
        $inter_vals = $data[2];
        $inter_arr = explode(",",$inter_vals);
        $inter_string = dropString($inter_arr, $inter_vends_arr, $dropDelta, $numCols, $bad_vendor); //then they are processed by dropString
        if($inter_string == "ERROR") {//if an ERROR is received then no row s inserted into the table and the next line in the csv is processed
            continue; //if there is an error in inter there will be one for all three, if there is not then they are all OK. This is because vendors
        } //provide rates for all three jurisdictions
        $intra_vends = $data[3];
        $intra_vends_arr = explode(",",$intra_vends);
        $intra_vals = $data[4];
        $intra_arr = explode(",",$intra_vals);
        $intra_string = dropString($intra_arr, $intra_vends_arr, $dropDelta, $numCols, $bad_vendor);

        $indeterminate_vends = $data[5];
        $indeterminate_vends_arr = explode(",",$indeterminate_vends);
        $indeterminate_vals = $data[6];
        $indeterminate_arr = explode(",",$indeterminate_vals);
        $indeterminate_string = dropString($indeterminate_arr, $indeterminate_vends_arr, $dropDelta, $numCols, $bad_vendor);

		$params = explode(',',"$code $inter_string $intra_string $indeterminate_string");
        query($conn, $stmt, $params, 28); //once a dropString has been created for the three rates, they are put together and inserted into the calc table
    }
}

/*If NO drop delta is provided then this function will skip that test increasing speed and just remove any costs from the vendor in $bad_vendor. The final set is then used to the populate the calc table.*/
function proccessVendorsNoDelta($conn, $internal, $calc_table, $temp_file, $numCols, $cols, $bad_vendor = NULL) {
    if (!$conn->query("SELECT `code`, `interVendors`, `inter_rates`, `intraVendors`, `intra_rates`, `indeterminateVendors`, `indeterminate_rates` FROM $internal INTO OUTFILE '$temp_file' FIELDS TERMINATED BY '|' LINES TERMINATED BY '\n'")) {
        die("ERROR: (26) ");
    } //first the internal table is dumped out to a csv so that the contents can be processed
	$num_qs = count(explode(',', $cols)) + 1;
	$qs = '?';
	for($i = 1; $i < $num_qs; $i++) {
		$qs .= ',?';
	}
	$stmt = prepare_stmt($conn, "INSERT INTO $calc_table (`code`, $cols) VALUES ($qs);", 29);
    $handle = fopen($temp_file, "r");
    while($data = fgetcsv($handle, 0, "|")) { //Then the csv is cycled through
        $output = "";
        $code = $data[0];

        $inter_vends = $data[1]; //the concatenated fields in the table are
        $inter_vends_arr = explode(",",$inter_vends); //broken up into arrays
        $inter_vals = $data[2];
        $inter_arr = explode(",",$inter_vals);
        $inter_string = dropStringNoDelta($inter_arr, $inter_vends_arr, $numCols, $bad_vendor); //then they are processed by dropStringNoDelta
        if($inter_string == "ERROR") {//if an ERROR is received then no row s inserted into the table and the next line in the csv is processed
            continue; //if there is an error in inter there will be one for all three, if there is not then they are all OK. This is because vendors
        } //provide rates for all three jurisdictions
        $intra_vends = $data[3];
        $intra_vends_arr = explode(",",$intra_vends);
        $intra_vals = $data[4];
        $intra_arr = explode(",",$intra_vals);
        $intra_string = dropStringNoDelta($intra_arr, $intra_vends_arr, $numCols, $bad_vendor);

        $indeterminate_vends = $data[5];
        $indeterminate_vends_arr = explode(",",$indeterminate_vends);
        $indeterminate_vals = $data[6];
        $indeterminate_arr = explode(",",$indeterminate_vals);
        $indeterminate_string = dropStringNoDelta($indeterminate_arr, $indeterminate_vends_arr, $numCols, $bad_vendor);
		
		$params = explode(',',"$code$inter_string$intra_string$indeterminate_string");
        query($conn, $stmt, $params, 30);
    }
}

/*This is where the actual string with the values is built and where the drops and necessary averaging takes place.*/
function dropString($vendor_arr, $vendor_name_arr, $dropDelta, $numCols, $bad_vendor = NULL) {
    if(!is_null($bad_vendor)) { //removes omitted vendors
        $index = array_search($bad_vendor, $vendor_name_arr);
        if (false !== $index) {
            unset($vendor_name_arr[$index]);
            $vendor_name_arr = array_values($vendor_name_arr);
            unset($vendor_arr[$index]);
            $vendor_arr = array_values($vendor_arr);
        }
    }
    $num_elems = count($vendor_arr);
	$output = "";
    if($dropDelta != -1 && $num_elems > 1) { //handles drop delta calculations if one is provided
        $delta = (($vendor_arr[1] - $vendor_arr[0]) / $vendor_arr[0]);
        if($delta >= $dropDelta) {
            unset($vendor_name_arr[0]);
            $vendor_name_arr = array_values($vendor_name_arr);
            unset($vendor_arr[0]);
            $vendor_arr = array_values($vendor_arr);
            $num_elems--;
        }
    }
    if ($num_elems >= $numCols) { //uses the values in the two arrays to build the string
        for($i = 0; $i < $numCols; $i++) {
            $output .= ",'$vendor_name_arr[$i]'";
            $output .= ",$vendor_arr[$i]";
        }
    } elseif ($num_elems > 0){ //uses what values are in the arrays and then uses the average to fill the missing fields
        $avg = 0;
        for($i = 0; $i < $num_elems; $i++) {
            $output .= ",'$vendor_name_arr[$i]'";
            $output .= ",$vendor_arr[$i]";
            $avg += $vendor_arr[$i];
        }
        $avg = (float)$avg/$num_elems;
        for($i = $num_elems; $i < $numCols; $i++) {
            $output .= ",'None'";
            $output .= ",$avg";
        }
    } else { //there are no vendors for this NPANXX so no rate can be given, an ERROR is returned
        return 'ERROR';
    }
    return $output;
}

/*If no dropDelta is provided then this function skips the test increasing speed.*/
function dropStringNoDelta($vendor_arr, $vendor_name_arr, $numCols, $bad_vendor = NULL) {
    if(!is_null($bad_vendor)) { //removes omitted vendors
        $index = array_search($bad_vendor, $vendor_name_arr);
        if (false !== $index) {
            unset($vendor_name_arr[$index]);
            $vendor_name_arr = array_values($vendor_name_arr);
            unset($vendor_arr[$index]);
            $vendor_arr = array_values($vendor_arr);
        }
    }
    $num_elems = count($vendor_arr);
    $output = "";
    if ($num_elems >= $numCols) { //uses the values in the two arrays to build the string
        for($i = 0; $i < $numCols; $i++) {
            $output .= ",'$vendor_name_arr[$i]'";
            $output .= ",$vendor_arr[$i]";
        }
    } elseif ($num_elems > 0){ //uses what values are in the arrays and then uses the average to fill the missing fields
        $avg = 0;
        for($i = 0; $i < $num_elems; $i++) {
            $output .= ",'$vendor_name_arr[$i]'";
            $output .= ",$vendor_arr[$i]";
            $avg += $vendor_arr[$i];
        }
        $avg = (float)$avg/$num_elems;
        for($i = $num_elems; $i < $numCols; $i++) {
            $output .= ",'None'";
            $output .= ",$avg";
        }
    } else { //there are no vendors for this NPANXX so no rate can be given, an ERROR is returned
        return 'ERROR';
    }
    return $output;
}

/*The cost table is created using a template, but filling it requires a dynamically created queries that takes the blended rate and applies it to the columns in the calc table*/
function createCostTable($conn, $blend_arr, $numCols, $calc_table, $cost_table, $internal) {
    if (!$conn->query("CREATE TABLE $cost_table LIKE z_finalRateTableTemplate")) {
        die("ERROR: (31) ");
    }

    if (!$conn->query("CREATE INDEX id_index ON $cost_table (code);")) {
        die("ERROR: (32) ");
    }

    if (!$conn->query("INSERT INTO $cost_table (`code`) SELECT `code` FROM $calc_table")) {
        die("ERROR: (33) ");
    }

    $inter_cost = ""; //this string will combine all of the individual rates into costs
    for($i = 0; $i < $numCols; $i++) { //uses the $numCols to build up the cost table query from the blend_arr
        $num = $i+1;
        $inter_cost .= "+ $blend_arr[$i]*$calc_table.inter_$num ";
    }
    $inter_cost = substr($inter_cost, 1);
    $intra_cost = str_replace("inter","intra", $inter_cost); //then an intra query is made
    $indeterminate_cost = str_replace("inter","indeterminate", $inter_cost);//and an indeterminate one


    if (!$conn->query("UPDATE $cost_table, $calc_table SET $cost_table.inter_rate = ($inter_cost), $cost_table.intra_rate = ($intra_cost), $cost_table.indeterminate_rate = ($indeterminate_cost) WHERE $cost_table.code=$calc_table.code;")) {
        die("ERROR: (34) ");
    }
}

/*to create the final table the cost table is queried by the price rules all the rows that fall within the given parameters have the given rule applied to them before thy are stored in the final table. If no pricing rules are provided then the cost table data is just copied into the final table*/
function createFinalTable($conn, $cost_table, $calc_table, $final_table, $internal, $RSID, $e_date) {

    if (!$conn->query("CREATE TABLE $final_table LIKE z_finalRateTableTemplate")) {
        die("ERROR: (35) ");
    }
    if (!$conn->query("CREATE INDEX id_index ON $final_table (code);")) {
        die("ERROR: (36) ");
    }
    if (!$conn->query("INSERT INTO $final_table (`code`) SELECT `code` FROM $calc_table")) {
        die("ERROR: (37) ");
    }

    if($_FILES['pricingRules'] && $_FILES['pricingRules']['size'] > 0) {
        processMarkUps($conn, $cost_table, $final_table);
    } else { //no mark up is applied. Final table is simply updated with cost data
        if (!$conn->query("UPDATE $final_table, $cost_table SET $final_table.inter_rate = ($cost_table.inter_rate), $final_table.intra_rate = ($cost_table.intra_rate), $final_table.indeterminate_rate = ($cost_table.indeterminate_rate) WHERE $final_table.code=$cost_table.code;")) {
            die("ERROR: (38) ");
        }
    }
	$stmt = prepare_stmt($conn, "UPDATE $final_table SET rsid = ?, effective_date = ?, min_time = '6', interval_time = '6';", 39);
	
	query($conn, $stmt, array($RSID, $e_date), 40);
}

/*This is where the actual mark ups are applied*/
function processMarkUps($conn, $cost_table, $final_table) {
    $file = $_FILES['pricingRules']['tmp_name'];
    $handle = fopen($file, 'r');
    while($data = fgetcsv($handle, 50, ',')) {
        $lowerBound = num_filter($data[0]);
        $upperBound = num_filter($data[1]);
        $operator = op_filter($data[2]);
        $amount = num_filter($data[3]);
        if (!$conn->query("UPDATE $final_table, $cost_table SET $final_table.inter_rate = ($amount"."$operator"."($cost_table.inter_rate)) WHERE $final_table.code=$cost_table.code AND $cost_table.inter_rate >= $lowerBound AND $cost_table.inter_rate < $upperBound;")) {
            die("ERROR: (41) ");
        }

        if (!$conn->query("UPDATE $final_table, $cost_table SET $final_table.intra_rate = ($amount"."$operator"."($cost_table.intra_rate)) WHERE $final_table.code=$cost_table.code AND $cost_table.intra_rate >= $lowerBound AND $cost_table.intra_rate < $upperBound;")) {
            die("ERROR: (42) ");
        }

        if (!$conn->query("UPDATE $final_table, $cost_table SET $final_table.indeterminate_rate = ($amount"."$operator"."($cost_table.indeterminate_rate)) WHERE $final_table.code=$cost_table.code AND $cost_table.indeterminate_rate >= $lowerBound AND $cost_table.indeterminate_rate < $upperBound;")) {
            die("ERROR: (43) ");
        }
    }
}

/*The final table is written out to a csv and then headers are added by addHeaders()*/
function writeOutCsv($conn, $table, $dir) {
    if (!$conn->query("SELECT `rsid`, `min_time`, `interval_time`, `effective_date`, `code`, `inter_rate`, `intra_rate`, `indeterminate_rate` FROM $table ORDER BY `code` INTO OUTFILE '$dir' FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n'")) {
        die("ERROR: (44) ");
    }
    addHeaders($dir, 'RateSetID,MBSeconds,IBSeconds,ReleaseDate,NPANXX,INTER,INTRA,Indeterminate_Jurisdiction');
}

/*This is an unfortunately slow process. In order to make a useful internal table data has to be collected from the cost and final table and added to the standard calc table. To get information on which rules were used however queries have to be done aagainst the cost table and then instead of using the rule associate with the range, it is stored in a column*/
function writeOutinternal($conn, $table, $cost_table, $final_table, $cols, $dir) {

    if (!$conn->query("ALTER TABLE $table ADD COLUMN `inter_cost` FLOAT(8,7), ADD COLUMN `inter_rule` VARCHAR(50), ADD COLUMN `inter_price` FLOAT(8,7), ADD COLUMN `intra_cost` FLOAT(8,7), ADD COLUMN `intra_rule` VARCHAR(50), ADD COLUMN `intra_price` FLOAT(8,7), ADD COLUMN `indeterminate_cost` FLOAT(8,7), ADD COLUMN `indeterminate_rule` VARCHAR(50), ADD COLUMN `indeterminate_price` FLOAT(8,7)")) {
        die("ERROR: (45) ");
    }
    if (!$conn->query("UPDATE $table, $cost_table SET $table.inter_cost = ($cost_table.inter_rate), $table.intra_cost = ($cost_table.intra_rate), $table.indeterminate_cost = ($cost_table.indeterminate_rate) WHERE $table.code=$cost_table.code;")) {
        die("ERROR: (46) ");
    }

    if($_FILES['pricingRules'] && $_FILES['pricingRules']['size'] > 0) {
        $file = $_FILES['pricingRules']['tmp_name'];
        $handle = fopen($file, 'r');
		$inter_stmt = prepare_stmt($conn, "UPDATE $table SET $table.inter_rule = ? WHERE $table.inter_cost >= ? AND $table.inter_cost < ?;", 47);
		$intra_stmt = prepare_stmt($conn, "UPDATE $table SET $table.intra_rule = ? WHERE $table.intra_cost >= ? AND $table.intra_cost < ?;", 48);
		$indet_stmt = prepare_stmt($conn, "UPDATE $table SET $table.indeterminate_rule = ? WHERE $table.indeterminate_cost >= ? AND $table.indeterminate_cost < ?;", 49);
        while($data = fgetcsv($handle, 50, ',')) {
            $lowerBound = num_filter($data[0]);
            $upperBound = num_filter($data[1]);
            $operator = op_filter($data[2]);
            $amount = num_filter($data[3]);
            query($conn, $inter_stmt, array("'$operator $amount'", $lowerBound, $upperBound), 47.5);
			query($conn, $intra_stmt, array("'$operator $amount'", $lowerBound, $upperBound), 48.5);
			query($conn, $indet_stmt, array("'$operator $amount'", $lowerBound, $upperBound), 49.5);
        }
    }

    if (!$conn->query("UPDATE $table, $final_table SET $table.inter_price = ($final_table.inter_rate), $table.intra_price = ($final_table.intra_rate), $table.indeterminate_price = ($final_table.indeterminate_rate) WHERE $table.code=$final_table.code;")) {
        die("ERROR: (50) ");
    }

    if (!$conn->query("SELECT * FROM $table ORDER BY `code` INTO OUTFILE '$dir' FIELDS TERMINATED BY ',' LINES TERMINATED BY '\n'")) {
        die("ERROR: (51) ");
    }
    $cols = str_replace("`","",$cols);
    $cols = 'code,' . $cols . ',inter_cost,inter_rule,inter_price,intra_cost,intra_rule,intra_price,indeterminate_cost,indeterminate_rule,indeterminate_price';
    addHeaders($dir, $cols);
}

/*Adds headers to a file*/
function addHeaders($file, $headers) {
    $tmpfile = fopen("$file.tmp","w");
    fwrite($tmpfile, $headers . "\n");
    fclose($tmpfile);
    exec("cat $file.tmp $file > $file.tmp2");
    exec("mv $file.tmp2 $file");
    exec("rm $file.tmp");
}

/*drops a list of tables*/
function dropTables($conn, $tables) {
    if (!$conn->query("DROP TABLE IF EXISTS $tables")) {
        echo("WARNING: (52) ");
    }
}
//Takes in an array of files addresses and an array of names you would like to call them and zips up all the files, deleting the old files and then returns the name of the zipped file.
function zipFolder($dir, $name, $file_dirs_arr, $file_names_arr) {
    $zip = new ZipArchive();
    $zip->open("$dir.zip", ZipArchive::CREATE | ZipArchive::OVERWRITE);

    // Create recursive directory iterator
    /** @var SplFileInfo[] $files */
    $numFiles = count($file_dirs_arr);
    for($i = 0; $i < $numFiles; $i++) {
        $zip->addFile($file_dirs_arr[$i], $file_names_arr[$i]);
    }

    // Zip archive will be created only after closing object
    $zip->close();
    for($i = 0; $i < $numFiles; $i++) {
        system("rm $file_dirs_arr[$i]");
    }
    return $name . ".zip";
}
