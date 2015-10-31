/*
 * Author: David S Slater
 * File: Customer.java
 * 
 * Helper Object for WeeklySummarizer
 */

import java.util.HashMap;

public class Total {
	public HashMap<String, Integer> durations = new HashMap<String, Integer>();
	public HashMap<String, Integer> billingSeconds = new HashMap<String, Integer>();
	public HashMap<String, Integer> charges = new HashMap<String, Integer>();
	public HashMap<String, Integer> costs = new HashMap<String, Integer>();
	public HashMap<String, Integer> attempted_calls = new HashMap<String, Integer>();
	public HashMap<String, Integer> completed_calls = new HashMap<String, Integer>();
	public int total_duration = 0, total_billing = 0, total_charge = 0, total_cost = 0, total_atp_calls = 0, total_comp_calls = 0;
}
