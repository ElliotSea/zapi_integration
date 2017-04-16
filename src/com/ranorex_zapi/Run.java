package com.ranorex_zapi;

import okhttp3.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Sergii on 3/20/17 for CareCloud
 *
 * This project is created for test execution results reporting
 * Input is PATH_TO_FILE ("RuntimeParameters.txt") of type JSON with pairs 'ScriptName - ExecutionResult'
 * Flow:
 * Check if there is already a Version with expected name in Jira (i.e. April-2017-Week2)
 * If not - one will be created and versionId will always have real id of the correct version
 * Then we will get list of components and their IDs from the Jira QA project
 * For each component we will see if there is TestCycle created for Today in format '2017-04-10-Appointments'
 * If not - one will be created
 * Then we will search Jira for all issues in Project QA with type Test and label Automated for each component
 * We will record them into automatedTestCases collection
 * Then we will parse file 'RuntimeParameters.txt' for test results, skipping those who don't have leading two
 * numbers in front: 1400_Login - skipped. 1400_04_Printing - counted. Each will be added to testExecutions collection
 * Then we will parse testExecutions separately for failed and success entries and work with them in two step:
 * Create new test execution in Zephyr; mark is with Pass/Fail status.
 */
public class Run {
	public static final String PROJECT_ID = "10502"; //Project QA
	public static final String URL_REST_BASE = "https://jira.carecloud.com/rest";
	public static final String PATH_TO_FILE = "RuntimeParameters.txt";
	private static final String BASIC_AUTHORIZATION = "Basic c2dhbGFnYW5pdWs6X0NhbGlmb3JuaWEx"; //credentials
	public static List<TestCase> automatedTestCases = new ArrayList<>(); //list of automated test cases from Jira

	public static String getSpecificVersionId(String expectedVersion){
		String versionId = "-2";
		Request request = constructRequest("/zapi/latest/util/versionBoard-list", new Pairs(new Pair("projectId",PROJECT_ID))).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject versionList = (JSONObject) obj;
			JSONArray arrayOfVersions = (JSONArray) versionList.get("unreleasedVersions");
			for (Object each : arrayOfVersions) {
				JSONObject version = (JSONObject) each;
				String versionLabel = (String) version.get("label");
				if (versionLabel.equals(expectedVersion)){
					versionId=(String) version.get("value");
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}

		if (versionId.equals("-2")){
			versionId = createNewVersion(expectedVersion);
		}
		return versionId;
	}

	public static String createNewVersion(String versionName){
		String versionId = "-2";
		JSONObject requestJSON = new JSONObject();
		requestJSON.put("name", versionName);
		requestJSON.put("description", "Automation Execution results on " + versionName);
		requestJSON.put("startDate", new SimpleDateFormat("yyyy-MM-01").format(Calendar.getInstance().getTime()));
		requestJSON.put("releaseDate", new SimpleDateFormat("yyyy-MM-28").format(Calendar.getInstance().getTime()));
		requestJSON.put("projectId", PROJECT_ID);
		Request request = constructRequest("/api/2/version", new Pairs())
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestJSON.toString()))
				.build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject jsonResponse = (JSONObject) obj;
			versionId = (String) jsonResponse.get("id");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return versionId;
	}

	public static Request.Builder constructRequest(String urlSuffix, Pairs queryParameters){
		HttpUrl.Builder urlBuilder = HttpUrl.parse(URL_REST_BASE + urlSuffix).newBuilder();
		for (Pair p : queryParameters.get()){
			urlBuilder.addQueryParameter(p.a(), p.b());
		}
		Request.Builder rb = new Request.Builder()
				.header("Authorization", BASIC_AUTHORIZATION)
				.url(urlBuilder.build().toString());
		return rb;
	}

	public static String getResponseFromAPI(Request request) {
		//System.out.println(request);
		OkHttpClient client = new OkHttpClient();
		Response response;
		String responseBody = null;
		try {
			response = client.newCall(request).execute();
			responseBody = response.body().string();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System.out.println(responseBody);
		return responseBody;
	}

	public static Map<String, List<String>> getComponentsAndCycleNames(Boolean isRegressionRelease) {
		List<String> cycleNames = new ArrayList<>();
		List<String> components = new ArrayList<>();
		Map<String, List<String>> map = new HashMap();
		Request request = constructRequest("/api/2/project/QA/components", new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONArray arrayOfComponents = (JSONArray) obj;
			for (Object each : arrayOfComponents) {
				JSONObject component = (JSONObject) each;
				components.add(component.get("name").toString());
				if (isRegressionRelease){
					cycleNames.add(component.get("name")+" "+"Regression");
				}else{
					cycleNames.add((new SimpleDateFormat("YYYY-MM-dd").format(Calendar.getInstance().getTime()))+"-"+component.get("name"));
				}

			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		map.put("cycleNames",cycleNames);
		map.put("components",components);
		return map;
	}

	public static void populateTestCasesList(List<String> components) {
		for (String component : components) {
			for (String testKey : getAutomatedTestKeysByComponent(component)) {
				if (getValidCommentFromJiraTest(testKey).equals(" ")) {continue;} //Skipping Jiras without comments
				//DEBUG System.out.println(testKey+" : "+getValidCommentFromJiraTest(testKey));
				automatedTestCases.add(new TestCase(
						getValidCommentFromJiraTest(testKey),
						testKey,
						getIdFromJiraTest(testKey)));
			}
		}
	}

	public static String getValidCommentFromJiraTest(String key) {
		String commentBody = null;
		Request request = constructRequest("/api/2/issue/" + key, new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject issue = (JSONObject) obj;
			JSONObject fieldsObject = (JSONObject) issue.get("fields");
			JSONObject comment = (JSONObject) fieldsObject.get("comment");
			JSONArray comments = (JSONArray) comment.get("comments");
			if (comments.size() == 0){ return " ";} //Skipping Jiras without comments
			int lowestCommentID = Integer.MAX_VALUE; //Looking for Comment with lowest ID (first comment)
			JSONObject firstComment = null;
			for (Object o : comments) {
				JSONObject each_comment = (JSONObject) o;
				int commentID = Integer.parseInt(each_comment.get("id").toString());
				if (commentID < lowestCommentID) {
					lowestCommentID = commentID;
					firstComment = each_comment;
				}
			}
			commentBody = firstComment.get("body").toString();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return commentBody;
	}

	public static String getIdFromJiraTest(String key) {
		String issueId = null;
		Request request = constructRequest("/api/2/issue/" + key, new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject issue = (JSONObject) obj;
			issueId = (String) issue.get("id");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return issueId;
	}

	public static ArrayList<String> getAutomatedTestKeysByComponent(String component) {
		ArrayList<String> automatedTestKeys = new ArrayList<>();
		Request request = constructRequest(
				"/api/2/search?jql=project=QA and type=Test and Automation=Automated and Component="+component+"&maxResults=200",
				new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject jsonObject = (JSONObject) obj;
			JSONArray results = (JSONArray) jsonObject.get("issues");
			System.out.println("There are " + results.size() + " Automated issues in " + component);
			for (Object o : results) {
				JSONObject issue = (JSONObject) o;
				String testKey = issue.get("key").toString();
				automatedTestKeys.add(testKey);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return automatedTestKeys;
	}

	public static Map<String,String> ensureTodaysCycleComponentExist(List<String> cycleNames, String versionId) {
		Map<String,String> cycleIdPair = new HashMap<>();
		Request request = constructRequest("/zapi/latest/cycle?projectId="+PROJECT_ID+"&versionId="+versionId,
				new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			List<String> defaultCyclesToIgnore = Arrays.asList("recordsCount", "-1");
			for (String cycleName : cycleNames) {
				boolean cycleExist = false;
				JSONObject cycles = (JSONObject) obj;
				for (Object o : cycles.keySet()) {
					String currentCycleID = (String) o;
					if (defaultCyclesToIgnore.contains(currentCycleID)) {
						continue;
					}
					JSONObject cycle = (JSONObject) cycles.get(currentCycleID);
					if (cycleName.equalsIgnoreCase((String) cycle.get("name"))) {
						cycleExist = true;
						System.out.println("Cycle " + cycleName + " already exist with id " + currentCycleID);
						cycleIdPair.put(cycleName, currentCycleID);
					}
				}
				if (!cycleExist) {
					cycleIdPair.put(cycleName, createNewTestCycle(cycleName, versionId));
				}
			}
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
		return cycleIdPair;
	}

	public static String createNewTestCycle(String newCycleName, String versionId) {
		String newTestCycleId = null;
		System.out.println("Creating new TestCycle with name " + newCycleName);
		JSONObject requestJSON = new JSONObject();
		requestJSON.put("name", newCycleName);
		requestJSON.put("description", "Automation Execution results on " + newCycleName);
		requestJSON.put("startDate", new SimpleDateFormat("d/MMM/yy").format(Calendar.getInstance().getTime()));
		requestJSON.put("endDate", new SimpleDateFormat("d/MMM/yy").format(Calendar.getInstance().getTime()));
		requestJSON.put("projectId", PROJECT_ID);
		requestJSON.put("versionId", versionId);
		Request request = constructRequest("/zapi/latest/cycle", new Pairs())
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestJSON.toString()))
				.build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject jsonResponse = (JSONObject) obj;
			System.out.println( (String) jsonResponse.get("responseMessage"));
			newTestCycleId = (String) jsonResponse.get("id");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newTestCycleId;
	}

	public static boolean checkIfExecutionAlreadyPresent(String automationName, List<TestExecution> testExecutions) {
		boolean found = false;
		for (TestExecution te : testExecutions) {
			found |= te.getAutomationName().equals(automationName);
		}
		return found;
	}

	public static List<TestExecution> populateTestExecutions(String pathToFile) {
		List<TestExecution> testExecutions = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(PATH_TO_FILE))) {
			String line = null;
			while ((line = br.readLine()) != null) {
				System.out.println(line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			Object obj = new JSONParser().parse(new FileReader(pathToFile));
			JSONObject jsonObject = (JSONObject) obj;
			JSONArray results = (JSONArray) jsonObject.get("ExecutionResults");
			for (Object r : results) {
				JSONObject test = (JSONObject) r;
				if (test.get("id").toString().contains("Login") ||
						test.get("id").toString().contains("LOGIN") ||
						test.get("id").toString().contains("TestCases_StatusReporter")){
					//if (!test.get("id").toString().matches(".*\\d+_\\d+.*")) { //filter out non functional tests (technical)
					// for example 100_Login is bad, but 200_02_LalalaLand is good.
					System.out.println("Skipping test execution: " + test.get("id"));
					continue;
				}
				if (checkIfExecutionAlreadyPresent((String) test.get("id"), testExecutions)) {
					System.out.println("Test execution " + test.get("id") + " is Already recorded in the test executions list");
				} else {
					System.out.println("Adding test execution: " + test.get("id") + " with result: " + test.get("result"));
					testExecutions.add(new TestExecution((String) test.get("id"), (String) test.get("result")));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return testExecutions;
	}

	public static List<String> getJiraIdsByAutomationName(String name) {
		List<String> jiraIds = new ArrayList<>();
		for (TestCase tc : automatedTestCases) {
			if (tc.getAutomationName().equals(name)) {
				jiraIds.add(tc.getJiraId());
			}
		}
		if (jiraIds.isEmpty()) System.out.println("while looking for Jira ID, this TC was not found: " + name);
		return jiraIds;
	}

	public static List<String> getJiraKeysByAutomationName(String name) {
		List<String> jiraKeys = new ArrayList<>();
		for (TestCase tc : automatedTestCases) {
			if (tc.getAutomationName().equals(name)) {
				jiraKeys.add(tc.getJiraKey());
			}
		}
		if (jiraKeys.isEmpty()) System.out.println("while looking for Jira Key, this TC was not found: " + name);
		return jiraKeys;
	}

	public static String getCycleIDForByJiraID(String jiraId, Map<String,String> cycleIdPair){
		String cycleId = "";
		Request request = constructRequest("/api/2/issue/" + jiraId, new Pairs()).build();
		try {
			Object obj = new JSONParser().parse(getResponseFromAPI(request));
			JSONObject issue = (JSONObject) obj;
			JSONObject fieldsObject = (JSONObject) issue.get("fields");
			JSONArray components = (JSONArray) fieldsObject.get("components");
			for (Object o : components) {
				JSONObject each_component = (JSONObject) o;
				String name = (String) each_component.get("name");
				for (String cycleName:cycleIdPair.keySet()){
					if (cycleName.contains(name)){
						cycleId = cycleIdPair.get(cycleName);
					}
				}
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return cycleId;
	}

	public static void addAndMarkTestsToCycle(List<String> jiraIds, ExecStatus status, String versionId, Map<String,String> cycleIdPair) {
		List<String> executionIds = new ArrayList<>();
		String statusCode;
		switch (status){
			case SUCCESS : statusCode = "1"; break;
			case FAILED : statusCode = "2"; break;
			default : statusCode = "3"; break;
		}

		System.out.println("Adding " + jiraIds.size() + " Test Cases to Zephyr");
		for (String jiraId : jiraIds) {
			JSONObject requestJSON = new JSONObject();
			requestJSON.put("cycleId", getCycleIDForByJiraID(jiraId, cycleIdPair));
			requestJSON.put("issueId", jiraId);
			requestJSON.put("projectId", PROJECT_ID);
			requestJSON.put("versionId", versionId);
			Request request = constructRequest("/zapi/latest/execution/", new Pairs())
					.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestJSON.toString()))
					.build();
			try {
				Object obj = new JSONParser().parse(getResponseFromAPI(request));
				JSONObject jsonResponse = (JSONObject) obj;
				executionIds.add((String) jsonResponse.keySet().toArray()[0]);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Marking "+executionIds.size()+" Test Executions in Zephyr with status " + status);
		for (String executionId : executionIds) {
			JSONObject requestJSON = new JSONObject();
			requestJSON.put("status", statusCode);
			Request request = constructRequest("/zapi/latest/execution/"+executionId+"/execute", new Pairs())
					.put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestJSON.toString()))
					.build();
			try {
				Object obj = new JSONParser().parse(getResponseFromAPI(request));
				JSONObject jsonResponse = (JSONObject) obj;
				if(!(jsonResponse.get("executionStatus")).equals(statusCode)){
					System.out.println("Something is wrong with "+executionId);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		String desiredVersion = new SimpleDateFormat("MMMM-YYYY").format(Calendar.getInstance().getTime())+
				"-Week"+Calendar.getInstance().get(Calendar.WEEK_OF_MONTH);
		System.out.println("Starting Regular Run with reporting into version: "+desiredVersion);
		String versionId = getSpecificVersionId(desiredVersion);
		List<String> components = getComponentsAndCycleNames(false).get("components");
		List<String> cycleNames = getComponentsAndCycleNames(false).get("cycleNames");
		Map<String, String> cycleIdPair = ensureTodaysCycleComponentExist(cycleNames, versionId);

		populateTestCasesList(components);
		System.out.println();
		System.out.println("Total number of Automated Test Cases in Jira: "+ automatedTestCases.size() + " ");

		List<TestExecution> testExecutions = populateTestExecutions(PATH_TO_FILE);
		System.out.println("Total number of Test Executions: "+testExecutions.size());

		List<String> jiraIDsAddSuccess = new ArrayList<>();
		List<String> jiraIDsAddFailed = new ArrayList<>();
		List<String> jiraKeysSuccess = new ArrayList<>();
		List<String> jiraKeysFailed = new ArrayList<>();

		for (TestExecution te : testExecutions) {
			if (te.getStatus().equals(ExecStatus.SUCCESS.toString())) {
				jiraIDsAddSuccess.addAll(getJiraIdsByAutomationName(te.getAutomationName()));
				jiraKeysSuccess.addAll(getJiraKeysByAutomationName(te.getAutomationName()));
			} else if (te.getStatus().equals(ExecStatus.FAILED.toString())) {
				jiraIDsAddFailed.addAll(getJiraIdsByAutomationName(te.getAutomationName()));
				jiraKeysFailed.addAll(getJiraKeysByAutomationName(te.getAutomationName()));
			} else System.out.println("Status is unknown");
		}

		for (String jiraKey : jiraKeysSuccess) {
			System.out.println(jiraKey+" - "+ ExecStatus.SUCCESS);
		}
		for (String jiraKey : jiraKeysFailed) {
			System.out.println(jiraKey+" - "+ ExecStatus.FAILED);
		}

		addAndMarkTestsToCycle(jiraIDsAddSuccess, ExecStatus.SUCCESS, versionId, cycleIdPair);
		addAndMarkTestsToCycle(jiraIDsAddFailed, ExecStatus.FAILED, versionId, cycleIdPair);
	}
}