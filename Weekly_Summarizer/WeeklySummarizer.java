/*
 * Author: David S Slater
 * File: WeeklySummarizer.java
 * 
 * Program reads in the previous week's summarized logs to make weekly totals by customer and for the entire system.
 * This information is stored as a .csv in the folder WeeklySummaries and is also emailed to ADDRESS.
 * Totaling is done backwards from last Sunday to last Monday.
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.json.JSONObject;

public class WeeklySummarizer {

	private final static String DIR_START = "DIRECTORY ROOT";
	private final static String FOLDER = "FOLDER TO OPERATE IN";
	
	private final static String REPORT_FROM = "EMAIL ADDRESS";
	private final static String REPORT_TO = "EMAIL ADDRESS";
	private final static String REPORT_SUBJECT = "Weekly ___ Report";
	
	private final static int _____DIVISOR = 10000;
	private final static int SECONDS_TO_MINUTES = 60;
	private final static int COLUMN_SIZE = 20; //width of columns in email
	static boolean EMAIL = true;
	
	public static void main(String[] args) throws Exception {
		GregorianCalendar calendar = new GregorianCalendar();
		handleArgs(args, calendar);
		setCalendarToLastSunday(calendar);
		String lastDay = getDate(calendar);
        Total total = new Total();
		for(int i = 0; i < 7; i++) { //goes through 7 days
			System.out.println("Processing: " + getDate(calendar));
			String dir = DIR_START + getDate(calendar);
			File[] fileList = new File(dir).listFiles(getJSONFilter()); //returns array of all JSON files in the folder
			if(fileList == null) {
				sendReport("Weekly Summarizer Error", "Weekly Summarizer could not finish because the folder " + dir + " was missing");
				System.exit(1);
			}
			for (int j = 0; j < fileList.length; j++) { //goes through all JSON files in the given day folder
				List<String>lines = Files.readAllLines(fileList[j].toPath(), StandardCharsets.UTF_8);
				if(lines.size() > 0)addToTotal(total, lines);
			}
			calendar.add(Calendar.DAY_OF_MONTH, -1); //moves back a day
		}
		calendar.add(Calendar.DAY_OF_MONTH, 1); //corrects for off by one problem with the for loop
		String firstDay = getDate(calendar);
		String fileName = DIR_START + FOLDER + firstDay + "--" + lastDay + ".csv";
		File totalsFile = new File(fileName);
		totalsFile.createNewFile(); //creates weekly summary file
		buildCSV(total, totalsFile, firstDay, lastDay);
		emailReport(totalsFile);
		System.out.println("Done.");
	}
	
	/*
	 * Processes the different command line argument and edits the calendar or global variables accordingly.
	 * It will either set the date, set a global variable to not send an email, or both.
	 */
	private static void handleArgs(String[] args, GregorianCalendar calendar) {
		if (args.length > 0) { // handles possible command line arguments
			if (args[0].equals("-h") || args[0].equals("-help")) printHelp();
			if (args[0].equals("-ne")) EMAIL = false;
			else {
				try {
					GregorianCalendar test = new GregorianCalendar();
					test.set(Integer.parseInt(args[0]), Integer.parseInt(args[1]) - 1, Integer.parseInt(args[2]));
					if(test.getTimeInMillis() > calendar.getTimeInMillis()) { //checks if date is in the future
						System.err.println("Error: Invalid Date. Exiting.");
						System.exit(1);
					}
					calendar.set(Integer.parseInt(args[0]), Integer.parseInt(args[1]) - 1, Integer.parseInt(args[2]));
				} catch (NumberFormatException e) {
					System.err.println("Improper arguments. Run with -h/-help for more information. Exiting.");
					System.exit(0);
				}
				if (args.length > 3) {
					if (args[3].equals("-ne")) EMAIL = false;
				}
			}
		}
	}
	
	/*
	 * Uses the current day of the week to back up the calendar to the previous Sunday. This assures that a full
	 * Monday to Sunday gets processed.
	 */
	private static void setCalendarToLastSunday(GregorianCalendar calendar) {
		int numDaysBack = Integer.parseInt(new SimpleDateFormat("u").format(calendar.getTime()));
		calendar.add(Calendar.DAY_OF_MONTH, -numDaysBack);
	}
	
	/*
	 * Returns a string with the formatted date
	 */
	private static String getDate(GregorianCalendar calendar) {
		return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
	}
	
	/*
	 * Returns a filter object that looks for .JSON files. It is used by the readAllFiles method
	 * to process all of the customers JSON information for a given day.
	 */
	private static FilenameFilter getJSONFilter() {
		FilenameFilter JSONFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
            	int lastIndex = name.lastIndexOf('.');
            	if(lastIndex>0) {
            	   String str = name.substring(lastIndex);
            	   if(str.equals(".JSON")) {
            		   return true;
            	   }
               }
               return false;
            }
        };
        return JSONFilter;
    }

	/*
	 * This method takes a files lines as a list, converts it into a string and then uses
	 * that string to create a JSONObject. Then, the JSONObject's fields are used to populate 
	 * the totals and HashMaps in the Total object.
	 */
	private static void addToTotal(Total total, List<String> lines) throws Exception {
		StringBuilder fullJSON = new StringBuilder();
		for (String line : lines) {
			fullJSON.append(line);
		}
		JSONObject customer = new JSONObject(fullJSON.toString());
		String name = customer.getString("account_name");
		int duration = customer.getInt("total_duration");
		int billing = customer.getInt("total_billing_secs");
		int charge = customer.getInt("total_charge");
		int cost = customer.getInt("total_cost");
		int atp_calls = customer.getInt("total_attempted_calls");
		int comp_calls = customer.getInt("total_completed_calls");
		if(total.durations.containsKey(name)) {
			total.durations.put(name, total.durations.get(name) + duration);
			total.billingSeconds.put(name, total.billingSeconds.get(name) + billing);
			total.charges.put(name, total.charges.get(name) + charge);
			total.costs.put(name, total.costs.get(name) + cost);
			total.attempted_calls.put(name, total.attempted_calls.get(name) + atp_calls);
			total.completed_calls.put(name, total.completed_calls.get(name) + comp_calls);
		} else {
			total.durations.put(name, duration);
			total.billingSeconds.put(name,  billing);
			total.charges.put(name, charge);
			total.costs.put(name, cost);
			total.attempted_calls.put(name, atp_calls);
			total.completed_calls.put(name, comp_calls);
		}
		total.total_duration += duration;
		total.total_billing += billing;
		total.total_charge += charge;
		total.total_cost += cost;
		total.total_atp_calls += atp_calls;
		total.total_comp_calls += comp_calls;
	}
	
	/*
	 * Using the information in the Total object, this method goes through each customer in the Totals 
	 * HashMaps and creates a comma delimited string for each one which is then written to the totals file.
	 * Grand totals are also added at the bottom of the file with an extra line separating the totals
	 * from the rest of the values. Data is also formatted to conventional standards as well as divided 
	 * into conventional units.
	 */
	private static void buildCSV(Total total, File totalsFile, String firstDay, String lastDay) throws Exception {
		FileOutputStream totalsStream = new FileOutputStream(totalsFile, false);
		String fields = "Name,Duration (min),Billable Seconds(min),Charge,Cost,Attempted Calls,Completed Calls,Start Date, End Date\n";
		totalsStream.write(fields.getBytes());
		DecimalFormat money = new DecimalFormat("$#.##");
		DecimalFormat time = new DecimalFormat("#.##");
		Set<String> keys = total.durations.keySet();
		for(String key : keys) {
			String duration = time.format((double)total.durations.get(key)/SECONDS_TO_MINUTES);
			String billing = time.format((double)total.billingSeconds.get(key)/SECONDS_TO_MINUTES);
			String charge = money.format((double)total.charges.get(key)/_____DIVISOR);
			String cost = money.format((double)total.costs.get(key)/_____DIVISOR);
			int atp_calls = total.attempted_calls.get(key);
			int comp_calls = total.completed_calls.get(key);
			String entry = key + "," + duration + "," + billing + "," + charge + "," + cost + "," + atp_calls + "," + comp_calls + "," + firstDay + "," + lastDay + "\n";
			totalsStream.write(entry.getBytes());
		}
		totalsStream.write('\n');
		String Tduration = time.format((double)total.total_duration/SECONDS_TO_MINUTES);
		String Tbilling = time.format((double)total.total_billing/SECONDS_TO_MINUTES);
		String Tcharge = money.format((double)total.total_charge/_____DIVISOR);
		String Tcost = money.format((double)total.total_cost/_____DIVISOR);
		int Tatp_calls = total.total_atp_calls;
		int Tcomp_calls = total.total_comp_calls;
		String totalEntry = "Totals" + "," + Tduration  + "," + Tbilling + "," + Tcharge + "," + Tcost + "," + Tatp_calls + "," + Tcomp_calls + "," + firstDay + "," + lastDay + "\n";
		totalsStream.write(totalEntry.getBytes());
		totalsStream.close();
	}
	
	/*
	 * Reads in CSV file previously built and creates a string that is dynamically spaced to create an ASCII
	 * table of the csv information. This string is then printed and sent the sendReport.
	 */
	private static void emailReport(File file) throws Exception {
		StringBuilder email = new StringBuilder();
		BufferedReader input = new BufferedReader(new FileReader(file));
		String line = null;
		while((line = input.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line, ",");
			while(st.hasMoreTokens()) {
				String data = st.nextToken();
				int Tbuffer = COLUMN_SIZE - data.length();
				if(Tbuffer > 0) {
					int left = Tbuffer/2;
					int right = Tbuffer - left;
					for(int i = 0; i < left; i++){
						email.append(" ");
					}
					email.append(data);
					for(int i = 0; i < right; i++) {
						email.append(" ");
					}
				} else {
					String new_data = data.substring(0,20);
					email.append(new_data);
				}
				email.append("|");
			}
			email.append("\n");
		}
		input.close();
		System.out.println(email.toString());
		if(EMAIL) sendReport(REPORT_SUBJECT, email.toString());
	}
	
	/*
	 * Takes the formatted String from emailReport as well as Global Variables for
	 * the sender, receiver, and subject and send an email of the report information.
	 */
	private static void sendReport(String subject, String message) throws MessagingException
    {
        final Properties p = new Properties();
        p.put("mail.smtp.host", "localhost");
        final Message msg = new MimeMessage(Session.getDefaultInstance(p));
        msg.setFrom(new InternetAddress(REPORT_FROM));
        msg.addRecipient(RecipientType.TO, new InternetAddress(REPORT_TO));
        msg.setSubject(subject);
        msg.setText(message);
        Transport.send(msg);
        System.out.println("Report Sent");
    }
	
	/*
	 * Method called by using this program with the -h or -help flags. Prints out instructions on how to use the program
	 * and then exits quietly.
	 */
	private static void printHelp() {
		System.out.println("");
		System.out.println("Purpose:");
		System.out.println("    This program will take ___ summaries for the previous week and create a week summary which is stored in the folder");
		System.out.println("    WeeklySummaries as well as email the summary to EMAIL ADDRESS");
		System.out.println("");
		System.out.println("Usage:");
		System.out.println("    Calling this program without arguments will result in a default run for last");
		System.out.println("    If called with arguements:");
		System.out.println("                   -h / -help:    Prints help message");
		System.out.println("                          -ne:    will not email report for last week");
		System.out.println("                   YYYY MM DD:    Will summarize for the week before the given date");
		System.out.println("               YYYY MM DD -ne:    Will summarize for the week before the given day, but will not email the report");
		System.out.println("    Any extra arguments will be ignored.");
		System.out.println("");
		System.exit(0);
	}
}
