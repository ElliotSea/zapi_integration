package com.ranorex_zapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ranorex_zapi.Run.*;

/**
 * Created by Sergii on 4/12/17.
 *
 * This class is used to report execution results to specific Version
 * Version name is passed in first argument by caller of com.ranorex_zapi.RegressionReleaseRun
 * Different testCycle naming strategy is used. flag 'isRegressionRelease' is responsible for switch
 */
public class RegressionReleaseRun {

	public static void main(String[] args) {
		String expectedVersion = args[0];
		System.out.println("Starting RegressionReleaseRun Run with reporting into version: "+expectedVersion);
		String versionId = getSpecificVersionId("March-2017-QA");
		List<String> components = getComponentsAndCycleNames(true).get("components");
		List<String> cycleNames = getComponentsAndCycleNames(true).get("cycleNames");
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
