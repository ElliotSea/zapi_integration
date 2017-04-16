# zapi_integration
Project to create custom parser and integrator with Jira's Zapi for execution reporting from automation frameworks

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
 
 RegressionReleseRun.java
 * This class is used to report execution results to specific Version
 * Version name is passed in first argument by caller of com.ranorex_zapi.RegressionReleaseRun
 * Different testCycle naming strategy is used. flag 'isRegressionRelease' is responsible for switch
