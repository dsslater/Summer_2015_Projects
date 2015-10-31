/*
 * Author: David S Slater
 * File: CDR_Summarizer.java
 * 
 * This program will use ___ API calls to grab all the CDRs for all clients. The raw information is stored in a folder
 * for the day in CSV files then the data is summarized by service and is stored in JSON files that are also in the
 * created folder. Next, the summaries are also added to a totals log called totals.csv which is in the root directory.
 * finally the summary information is written to a file in log/charge_posts/ for the php script to read and post to the
 * ___ server. This file is '|' delimited and is deleted after the php script is run.
 * 
 * JSON SPEC:
 * {
 * 		"services":
 * 			[
 * 				{"SERVICE#1":
 * 					[
 * 						{"charge":#},
 * 						{"cost":#}
 * 					]
 * 				},
 * 				{"SERVICE#2":
 * 					[
 * 						{"charge":#},
 * 						{"cost":#}
 * 					]
 * 				}
 * 			],
 * 		"total_duration":#,
 * 		"account_id":"ACCOUNT ID",
 * 		"total_cost":#,
 * 		"total_charge":#,
 * 		"date":"YYYY-MM-DD",
 * 		"account_name":"NAME OF ACCOUNT"
 * }
 */
import java.net.*;
import java.io.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Set;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.String;

public class CDR_Summarizer {

	private final static String KEY_URL = "URL TO GET KEY";
	private final static String KEY_INPUT = "PAYLOAD FOR KEY";
	private final static String CUSTOMERS_URL = "URL TO GET CHILDREN";
	private final static String CDR_URL_1 = "FIRST PIECE OF CDR URL";
	private final static String CDR_URL_2 = "SECOND PIECE OF CDR URL";
	private final static String CDR_URL_3 = "THIRD PIECE OF CDR URL";
	private final static String TEMP_POST_DIR = "TEMPORARY FILE WRITEOUT";
	private final static long GREG_SECS = 62167219200L;
	private final static String TOTALS_DIR = "LOCATIONS TO STORE TOTALS LOG DATA";
	private final static String DIR_START = "DIRECTORY ROOT";
	
	static int DAYS_BEFORE_TODAY = 1;
	static boolean POST = true;
	static String DATE = "";
	
	/*
	 * Handles command line arguments, gets a list of customers and then cycles through them summarizing, 
	 * saving, and posting their information
	 */
	public static void main(String[] args) throws Exception {
		GregorianCalendar calendar = new GregorianCalendar();
		if (args.length > 0) { // handles possible command line arguments
			if (args[0].equals("-h") || args[0].equals("-help")) printHelp();
			if (args[0].equals("-np")) POST = false;
			else {
				try {
					calendar.set(Integer.parseInt(args[0]), Integer.parseInt(args[1]) - 1, Integer.parseInt(args[2]));
					DAYS_BEFORE_TODAY = 0;
				} catch (NumberFormatException e) {
					System.err.println("Improper arguments. Run with -h/-help for more information. Exiting.");
					System.exit(0);
				}
				if (args.length > 3) {
					if (args[3].equals("-np")) POST = false;
				}
			}
		}
		String from = getFrom(calendar); // gets the time ranges for the query in gregorian seconds
		String to = getTo(calendar);
		DATE = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
		String dirName = DIR_START + DATE;
		new File(dirName).mkdir(); // creates directory for logs based off of date
		FileOutputStream totalStream = setUpTotals(new File(TOTALS_DIR));
		String key = getKey();
		JSONArray arr = getCustomers(key).getJSONArray("data");
		int size = arr.length();
		for (int i = 0; i < size; i++) { // cycles through array of customers
			summarize((JSONObject)arr.get(i), key, from, to, dirName, totalStream);
		}
		totalStream.close();
		System.out.println("Done.");
	}
	
	/*
	 * Called for each customer. Summarize stores a copy of customers CDRs, then retrieves a JSON copy
	 * It then passes the JSON to getSummary. This method returns a summarized version of the CDRs.
	 * This summarized JSON is then written to a file in the date directory.
	 */
	private static void summarize(JSONObject customer, String key, String from, String to, String dirName, FileOutputStream totalStream) throws Exception
	{
		String actID = customer.getString("id");
		String actName = customer.getString("name");
		storeCSV(actID, key, from, to, dirName, actName); // stores a copy of all CDRs in the log folder
		JSONObject CDRs = getCDRs(actID, key, from, to); // gets CDRs in JSON form
		JSONObject summary = getSummary(CDRs, actID, actName, totalStream, DATE); // the CDRs are summarized
		File JSONFile = new File(dirName + "/" + actName + ".JSON"); // puts summarized JSON in log
		JSONFile.createNewFile();
		FileOutputStream JSONStream = new FileOutputStream(JSONFile, false);
		JSONStream.write(summary.toString().getBytes());
		JSONStream.write('\n');
		JSONStream.close();
	}

	/*
	 * This method uses three hashmaps and three total variables to keep track
	 * of summary data. \ The method moves through each CDR in the JSON CDRs and
	 * adds the service and it cost, charge, and duration to the hashmap. This
	 * way different service are segregated while still being summed All of this
	 * information is then passed to buildSummary to actually create the JSON
	 */
	private static JSONObject getSummary(JSONObject CDRs, String actID, String actName, FileOutputStream totalStream, String date) throws Exception {
		JSONArray CDR_arr = CDRs.getJSONArray("data");
		int num_cdrs = CDR_arr.length(), total_duration = 0;
		double totalCharge = 0, total_cost = 0;
		HashMap<String, Double> charges = new HashMap<String, Double>(), reseller_costs = new HashMap<String, Double>();
		HashMap<String, Integer> durations = new HashMap<String, Integer>();
		for (int j = 0; j < num_cdrs; j++) {
			JSONObject CDR = (JSONObject) CDR_arr.get(j);
			String test = CDR.getString("rate_name");
			double charge = CDR.getDouble("cost");
			double cost = CDR.getDouble("reseller_cost");
			int duration = CDR.getInt("duration_seconds");
			if (charges.containsKey(test)) {
				charges.put(test, charges.get(test) + charge);
				reseller_costs.put(test, reseller_costs.get(test) + cost);
				durations.put(test, durations.get(test) + duration);
			} else {
				charges.put(test, charge);
				reseller_costs.put(test, cost);
				durations.put(test, duration);
			}
			totalCharge += charge;
			total_cost += cost;
			total_duration += duration;
		}
		return buildSummary(actName, actID, charges, reseller_costs,
				durations, totalCharge, total_cost, total_duration,
				totalStream, date);
	}

	/*
	 * Takes in all the information created by getSummary and builds a
	 * JSONObject to store all of the fields. The spec can be seen at the top of
	 * this page. This is also where the information gets written to totals in
	 * csv format.
	 */
	private static JSONObject buildSummary(String actName, String actID, HashMap<String, Double> charges,
			HashMap<String, Double> reseller_costs, HashMap<String, Integer> durations, double totalCharge,
			double total_cost, int total_duration, FileOutputStream totalStream, String date) throws Exception {
		JSONObject CDR_Build = new JSONObject();
		CDR_Build.put("account_name", actName); //starts building a JSONObject for the entire customer
		CDR_Build.put("account_id", actID);
		JSONArray services = new JSONArray(); //array of all services
		for (String s : charges.keySet()) { //builds up a service JSONObject and adds it to the JSON array for the services
			JSONArray values = new JSONArray();
			JSONObject charge_value = new JSONObject("{charge:" + charges.get(s) + "}");
			JSONObject cost_value = new JSONObject("{cost:" + reseller_costs.get(s) + "}");
			JSONObject duration_value = new JSONObject("{duration:" + durations.get(s) + "}");
			values.put(charge_value);
			values.put(cost_value);
			values.put(duration_value);
			JSONObject service = new JSONObject();
			service.put(s, values);
			services.put(service);
			String output = actName + "," + actID + "," + date + "," + s + "," + durations.get(s) + "," + charges.get(s) + "," + reseller_costs.get(s) + "\n";
			totalStream.write(output.getBytes()); //writing to the totals file is done here to not repeat cycling through the services
		}
		CDR_Build.put("services", services);
		CDR_Build.put("total_charge", totalCharge);
		CDR_Build.put("total_cost", total_cost);
		CDR_Build.put("total_duration", total_duration);
		CDR_Build.put("date", date);
		System.out.println("Summarized: " + actName);
		if (POST) { //if the information is being posted to the server then writePostFile is called.
			writePostFile(actID, totalCharge);
			System.out.println("Stored Charges for: " + actName);
		}
		return CDR_Build; //return customers summarized JSONObject
	}
	
	/*
	 * Handles all API calls. Takes in the URL, RequestProperties and any other pertinent information
	 * and returns a BufferedReader pointing to the server response. WARNING: does not close connection
	 * relies on garbage collection/server decision making
	 */
	private static BufferedReader getResponse(String urlString, boolean output, String type, HashMap<String, String> properties, String input) throws Exception {
		URL url = new URL(urlString);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		if(output)connection.setDoOutput(true);
		connection.setRequestMethod(type);
		Set<String> keys = properties.keySet();
		for(String key : keys)
			connection.setRequestProperty(key, properties.get(key));
		if(input != null) {
			OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
			out.write(input);
			out.close();
		}
		if (connection.getResponseCode() >= 400) throw new Exception("The ___ API call to get the csv stream was unable to properly connect  Response Code: " + connection.getResponseCode());
		return new BufferedReader(new InputStreamReader(connection.getInputStream()));
	}

	/*
	 * Returns an Authentication key as a String. Uses the JSON input to fulfill
	 * the PUT request.
	 */
	private static String getKey() throws Exception {
		HashMap<String, String> props = new HashMap<String, String>();
		props.put("Accept", "application/json");
		BufferedReader in = getResponse(KEY_URL, true, "PUT", props, KEY_INPUT);
		StringBuilder response = new StringBuilder();
		String next;
		while ((next = in.readLine()) != null) {
			response.append(next);
		}
		JSONObject request = new JSONObject(response.toString());
		in.close();
		return request.getString("auth_token");
	}

	/*
	 * Returns a JSONObject that contains an array of accounts. Uses the
	 * Authentication key to fulfill the GET request.
	 */
	private static JSONObject getCustomers(String key) throws Exception {
		HashMap<String, String> props = new HashMap<String, String>();
		props.put("Accept", "application/json");
		props.put("X-Auth-Token", key);
		BufferedReader in = getResponse(CUSTOMERS_URL, true, "GET", props, null);
		StringBuilder response = new StringBuilder();
		String next;
		while ((next = in.readLine()) != null) {
			response.append(next);
		}
		JSONObject customers = new JSONObject(response.toString());
		in.close();
		return customers;
	}

	/*
	 * Returns a JSONObject that contains an array of an account's CDRs for the
	 * given time frame. Uses the Authentication key to fulfill the GET request.
	 */
	private static JSONObject getCDRs(String id, String key, String from, String to) throws Exception {
		HashMap<String, String> props = new HashMap<String, String>();
		props.put("Accept", "application/json");
		props.put("X-Auth-Token", key);
		String url = CDR_URL_1 + id + CDR_URL_2 + from + CDR_URL_3 + to;
		BufferedReader in = getResponse(url, true, "GET", props, null);
		StringBuilder response = new StringBuilder();
		String next;
		while ((next = in.readLine()) != null) {
			response.append(next);
		}
		JSONObject CDRs = new JSONObject(response.toString());
		in.close();
		return CDRs;
	}

	/*
	 * Uses the ___ API to GET a csv version of an accounts CDRs. This information is then stored in a log file
	 */
	private static void storeCSV(String id, String key, String from,String to, String dirName, String actName) throws Exception {
		HashMap<String, String> props = new HashMap<String, String>();
		props.put("Accept", "application/octet-stream");
		props.put("X-Auth-Token", key);
		String url = CDR_URL_1 + id + CDR_URL_2 + from + CDR_URL_3 + to;
		BufferedReader in = getResponse(url, true, "GET", props, null);
		File csvFile = new File(dirName + "/" + actName + ".csv");
		csvFile.createNewFile();
		FileOutputStream csvStream = new FileOutputStream(csvFile, false);
		String temp = null;
		while ((temp = in.readLine()) != null) {
			csvStream.write(temp.getBytes());
			csvStream.write('\n');
		}
		csvStream.close();
		in.close();
	}
	
	/*
	 * Sets up csv file putting header of fields if necessary. Returns OutputStream.
	 */
	private static FileOutputStream setUpTotals(File totalFile) throws Exception
	{
		FileOutputStream totalStream = null;
		if (!totalFile.exists()) { // adds fields line if the totals file is being created
			totalFile.createNewFile();
			totalStream = new FileOutputStream(totalFile, true);
			String fields = "actName, actID, date, service, duration, charge, cost\n";
			totalStream.write(fields.getBytes());
		} else {
			totalStream = new FileOutputStream(totalFile, true);
		}
		return totalStream;
	}

	/*
	 * Uses the calendar to get the Gregorian seconds at the start of the test
	 * day
	 */
	private static String getFrom(GregorianCalendar calendar) {
		calendar.add(Calendar.DAY_OF_MONTH, -DAYS_BEFORE_TODAY);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		System.out.print("From: " + calendar.getTime() + " ->");
		return String.valueOf((calendar.getTimeInMillis() / 1000) + GREG_SECS);
	}

	/*
	 * Uses the calendar to get the Gregorian seconds at the end of the test day. Must be called after getFrom
	 */
	private static String getTo(GregorianCalendar calendar) {
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		System.out.println("  To: " + calendar.getTime());
		return String.valueOf((calendar.getTimeInMillis() / 1000) + GREG_SECS);
	}

	/*
	 * Help method that prints out instructions on how to use the executable
	 */
	private static void printHelp() {
		System.out.println("");
		System.out.println("Purpose:");
		System.out.println("    This program will use ___ API calls to grab all the CDRs for all clients. The raw information is stored in a folder");
		System.out.println("    for the day in CSV files then the data is summarized by service and is stored in JSON files that are also in the");
		System.out.println("    creates folder Finally the summaries are also added to a totals log called totals.csv which is in the root directory.");
		System.out.println("");
		System.out.println("Usage:");
		System.out.println("    Calling this program without arguments will result in a default run for yesterday");
		System.out.println("    If called with arguements:");
		System.out.println("                   -h / -help:    Prints help message");
		System.out.println("                          -np:    will not post totals to ___ for billing");
		System.out.println("                   YYYY MM DD:    Will summarize for the given day and post totals to ___");
		System.out.println("               YYYY MM DD -np:    Will summarize for the given day, but will not post to ___");
		System.out.println("    Any extra arguments will be ignored.");
		System.out.println("");
		System.exit(0);
	}
	
	/*
	 * Writes information for usage out to a temporary file that gets read by the php script 
	 * that will end up posting to the ___ serve
	 */
	private static void writePostFile(String actID, double totalCharge)throws IOException {
		String dirName = DIR_START + TEMP_POST_DIR; 
		File postFile =	new File(dirName); 
		if (!postFile.exists()) {  
			postFile.createNewFile();
		}
		FileOutputStream postStream = new FileOutputStream(postFile, true);
		String year = DATE.substring(0, 4);
		String month = DATE.substring(5, 7);
		String day = DATE.substring(8, 10);
		String post = actID + "|" + totalCharge + "|" + year + "|" + month + "|" + day +"\n";
		postStream.write(post.getBytes());
		postStream.close();
	}
}
