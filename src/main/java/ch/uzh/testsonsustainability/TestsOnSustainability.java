package ch.uzh.testsonsustainability;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unisa.testSmellDiffusion.main.CalculateMetrics;
import it.unisa.testSmellDiffusion.main.StaticAnalysisOutput;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.api.Git;
import org.kohsuke.github.GHIssueState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Date;
import java.util.List;
import java.util.Map;


public class TestsOnSustainability {

    private static Logger LOGGER = LoggerFactory.getLogger(TestsOnSustainability.class);


    private static Options createCLIOptions() {
        Options options = new Options();
        options.addOption("d", "csv-data-path", true, "Path where the cvs input data is located");
        options.addOption("o", "out-path", true, "Path where the csv output data will be stored");
        options.addOption("c", "clone-path", true, "Path where the clone of the repos will be stored");
        options.addOption("y", "starting-year", true, "Starting year for the analysis");
        options.addOption("u", "github-username", true, "Username for accessing the github api");
        options.addOption("t", "github-token", true, "Access token for accessing the github api");
        options.addOption("j", "job-api-base-url", true, "Url of the base REST API to get next job to execute");
        options.addOption("h", "help", false, "Print this message");

        return options;
    }


    private static void exitWithError(String error, HelpFormatter formatter, Options options) {
        System.err.println(error);
        formatter.printHelp(Utils.APPLICATION_NAME, options);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {

        String csvPath = null;
        String outPath = null;
        String clonePath = null;
        int startingYear = 2019;
        String gitHubUsername = null;
        String gitHubToken = null;
        String jobApiBaseUrl = null;

        CommandLineParser parser = new DefaultParser();
        Options options = createCLIOptions();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine line = parser.parse( options, args );
            if (line.hasOption("h") || line.getOptions().length == 0) {
                formatter.printHelp( Utils.APPLICATION_NAME, options);
                System.exit(0);
            } else {
                if (line.hasOption("d")) {
                    csvPath = Utils.normalizePath(line.getOptionValue("d"));
                } else {
                    exitWithError("Option --csv-data-path is mandatory", formatter, options);
                }
                if (line.hasOption("o")) {
                    outPath = Utils.normalizePath(line.getOptionValue("o"));
                } else {
                    exitWithError("Option --out-path is mandatory", formatter, options);
                }
                if (line.hasOption("c")) {
                    String cloneRoot = Utils.normalizePath(line.getOptionValue("c"));
                    String cloneSubDir = String.valueOf(Instant.now().getEpochSecond());
                    clonePath = Utils.normalizePath(cloneRoot + "run-" + cloneSubDir);
                    Files.createDirectories(Paths.get(clonePath));
                } else {
                    exitWithError("Option --clone-path is mandatory", formatter, options);
                }
                if (line.hasOption("y")) {
                    startingYear = Integer.parseInt(line.getOptionValue("y"));
                } else {
                    exitWithError("Option --starting-year is mandatory", formatter, options);
                }
                if (line.hasOption("u")) {
                    gitHubUsername = line.getOptionValue("u");
                } else {
                    exitWithError("Option --github-username is mandatory", formatter, options);
                }
                if (line.hasOption("t")) {
                    gitHubToken = line.getOptionValue("t");
                } else {
                    exitWithError("Option --github-token is mandatory", formatter, options);
                }
                if (line.hasOption("j")) {
                    jobApiBaseUrl = line.getOptionValue("j");
                } else {
                    exitWithError("Option --job-api-url is mandatory", formatter, options);
                }
            }
        }
        catch( ParseException exp ) {
            System.err.println( "Parsing of command line arguments failed.  Reason: " + exp.getMessage());
            formatter.printHelp( Utils.APPLICATION_NAME, options);
            System.exit(1);
        }

        runAnalysis(csvPath, outPath, clonePath, startingYear, Pair.of(gitHubUsername, gitHubToken), jobApiBaseUrl);

    }

    private static void runAnalysis(String csvPath, String outPath, String clonePath, int startingYear, Pair<String, String> ghUserPassword, String jobApiBaseUrl) throws Exception {
        MavenCentralInfo mavenCentralInfo = new MavenCentralInfo(csvPath);
        Domains domains = new Domains(csvPath);

        String csvFileInputPath = csvPath + Utils.CSV_INPUT_FILENAME;
        Map<String, PrintWriter> csvOutput = Utils.csvOutput(outPath);

        LocalDate startingDate = LocalDate.of(startingYear, Month.JANUARY, 1);
        LocalDate today = LocalDate.now();

        List<String> yearMonthBetweenDates = Lists.newArrayList();
        yearMonthBetweenDates.add("Project name");
        for (LocalDate iteratorDate = startingDate; iteratorDate.isBefore(today); iteratorDate = iteratorDate.plusMonths(1)) {
            yearMonthBetweenDates.add(iteratorDate.getYear() + "-" + iteratorDate.getMonth());
        }

        String fileHeaderString = String.join(",", yearMonthBetweenDates);
        for (PrintWriter printWriter : csvOutput.values()) {
            printWriter.println(fileHeaderString);
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            Job job = restTemplate.getForObject(jobApiBaseUrl + Utils.GET_JOB_TO_DO_ENDPOINT, Job.class);
            while (job != null) {
                String projName = job.getProject();
                String cloneLink = job.getGithubLink();
                LOGGER.info("Got job to execute with id = {}, project = {}, and github link = {}", job.getId(), projName, cloneLink);

                try {
                    File cloneDir = new File(clonePath + "/" + projName.replace(":", "/"));
                    cloneDir.mkdirs();
                    Git git = Git.cloneRepository()
                            .setURI(cloneLink)
                            .setDirectory(cloneDir)
                            .call();
                    String repoName = cloneLink.split("github.com/")[1];

                    GithubAPI github = new GithubAPI(repoName, domains, startingYear, git, ghUserPassword);

                    Map<String, StringBuilder> variableValues = Maps.newHashMap();
                    for (String variableName : csvOutput.keySet()) {
                        variableValues.put(variableName, new StringBuilder());
                    }

                    Date untilDate = new Date();

                    for (LocalDate iteratorDate = startingDate; iteratorDate.isBefore(today); iteratorDate = iteratorDate.plusMonths(1)) {

                        LocalDate sinceLocalDate = iteratorDate;
                        LocalDate untilLocalDate = sinceLocalDate.plusMonths(1);
                        // we need the deprecated java date type to use the GHAPI library
                        Date sinceDate = new Date(sinceLocalDate.getYear() - 1900, sinceLocalDate.getMonthValue() - 1, sinceLocalDate.getDayOfMonth());
                        untilDate = new Date(untilLocalDate.getYear() - 1900, untilLocalDate.getMonthValue() - 1, untilLocalDate.getDayOfMonth());

                        LOGGER.info("Starting github extraction");

                        variableValues.get("commits").append(",").append(github.getNumberOfCommits(sinceDate, untilDate));
                        variableValues.get("contributors").append(",").append(github.getNumberOfContributors(sinceDate, untilDate));
                        variableValues.get("u_contributors").append(",").append(github.getNumberOfUniversityContributors(sinceDate, untilDate));
                        variableValues.get("c_contributors").append(",").append(github.getNumberOfCommercialContributors(sinceDate, untilDate));

                        variableValues.get("issues").append(",").append(github.getNumberOfIssues(GHIssueState.ALL, sinceDate, untilDate));
                        variableValues.get("non_dev_issues").append(",").append(github.getNumberOfNonDevIssues(GHIssueState.ALL, sinceDate, untilDate));
                        variableValues.get("submitters").append(",").append(github.getNumberOfSubmitters(GHIssueState.ALL, sinceDate, untilDate));
                        variableValues.get("non_dev_submitters").append(",").append(github.getNumberOfNonDevSubmitters(GHIssueState.ALL, sinceDate, untilDate));

                        variableValues.get("open_issues").append(",").append(github.getNumberOfIssues(GHIssueState.OPEN, sinceDate, untilDate));
                        variableValues.get("non_dev_open_issues").append(",").append(github.getNumberOfNonDevIssues(GHIssueState.OPEN, sinceDate, untilDate));
                        variableValues.get("open_issues_submitters").append(",").append(github.getNumberOfSubmitters(GHIssueState.OPEN, sinceDate, untilDate));
                        variableValues.get("non_dev_open_issues_submitters").append(",").append(github.getNumberOfNonDevSubmitters(GHIssueState.OPEN, sinceDate, untilDate));

                        variableValues.get("closed_issues").append(",").append(github.getNumberOfIssues(GHIssueState.CLOSED, sinceDate, untilDate));
                        variableValues.get("non_dev_closed_issues").append(",").append(github.getNumberOfNonDevIssues(GHIssueState.CLOSED, sinceDate, untilDate));
                        variableValues.get("closed_issues_submitters").append(",").append(github.getNumberOfSubmitters(GHIssueState.CLOSED, sinceDate, untilDate));
                        variableValues.get("non_dev_closed_issues_submitters").append(",").append(github.getNumberOfNonDevSubmitters(GHIssueState.CLOSED, sinceDate, untilDate));

                        String artifactTag = mavenCentralInfo.getCurrentVersionArtifactTag(projName, untilLocalDate);
                        if (mavenCentralInfo.artifactTagExists(artifactTag)) {
                            variableValues.get("upstreams").append(",").append(mavenCentralInfo.getDependencies(artifactTag).size());
                            variableValues.get("downstreams").append(",").append(mavenCentralInfo.getDependants(artifactTag).size());
                            variableValues.get("t_upstreams").append(",").append(mavenCentralInfo.getTransitiveDependencies(artifactTag).size());
                            variableValues.get("t_downstreams").append(",").append(mavenCentralInfo.getTransitiveDependants(artifactTag).size());
                            variableValues.get("d_upstreams").append(",").append(mavenCentralInfo.getDormantDependencies(artifactTag, untilLocalDate, github).size());
                            variableValues.get("q90").append(",").append(github.getSizeOfTheCoreTeam(sinceDate, untilDate));
                            variableValues.get("cc_degree").append(",").append(github.getSocialTies(sinceDate, untilDate));
                            variableValues.get("dc_katz").append(",").append(mavenCentralInfo.katzCentrality(projName, untilLocalDate));
                        } else {
                            LOGGER.error("Artifact tag {} not found in maven dependency graph. Thus setting all maven dependent variables to null", artifactTag);
                            List<String> mavenVariables = Lists.newArrayList("upstreams", "downstreams", "t_upstreams", "t_downstreams", "d_upstreams", "q90", "cc_degree", "dc_katz");
                            for (String mavenVariable : mavenVariables) {
                                variableValues.get(mavenVariable).append(", null");
                            }
                        }

                        String branch = github.getBranchAtDate(untilDate);

                        LOGGER.info("Starting calculation of static factors");
                        try {
                            StaticAnalysisOutput output = CalculateMetrics.calculateMetrics(cloneDir.getAbsolutePath(), branch);
                            variableValues.get("ar").append(",").append(output.getAr());
                            variableValues.get("dc").append(",").append(output.getDc());
                            variableValues.get("ec_t").append(",").append(output.getEcJUnit());
                            variableValues.get("ec_p").append(",").append(output.getEcProject());
                            variableValues.get("et").append(",").append(output.getEt());
                            variableValues.get("fto").append(",").append(output.getFto());
                            variableValues.get("cdsbp").append(",").append(output.getIsCDSBP());
                            variableValues.get("cc").append(",").append(output.getIsComplexClass());
                            variableValues.get("fd").append(",").append(output.getIsFuctionalDecomposition());
                            variableValues.get("gc").append(",").append(output.getIsGodClass());
                            variableValues.get("mc").append(",").append(output.getIsMisplacedClass());
                            variableValues.get("sc").append(",").append(output.getIsSpaghettiCode());
                            variableValues.get("it").append(",").append(output.getIt());
                            variableValues.get("loc_t").append(",").append(output.getLocJUnit());
                            variableValues.get("loc_p").append(",").append(output.getLocProject());
                            variableValues.get("lt").append(",").append(output.getLt());
                            variableValues.get("mg").append(",").append(output.getMg());
                            variableValues.get("noc").append(",").append(output.getNumberOfClasses());
                            variableValues.get("notc").append(",").append(output.getNumberOfTestClasses());
                            variableValues.get("ro").append(",").append(output.getRo());
                            variableValues.get("se").append(",").append(output.getSe());
                            variableValues.get("st").append(",").append(output.getSmellyJUnit());
                            variableValues.get("wmc_t").append(",").append(output.getWmcJUnit());
                            variableValues.get("wmc_p").append(",").append(output.getWmcProject());
                        } catch (Exception e) {
                            LOGGER.error(e.getMessage());
                            variableValues.get("ar").append(",").append("ERROR");
                            variableValues.get("dc").append(",").append("ERROR");
                            variableValues.get("ec_t").append(",").append("ERROR");
                            variableValues.get("ec_p").append(",").append("ERROR");
                            variableValues.get("et").append(",").append("ERROR");
                            variableValues.get("fto").append(",").append("ERROR");
                            variableValues.get("cdsbp").append(",").append("ERROR");
                            variableValues.get("cc").append(",").append("ERROR");
                            variableValues.get("fd").append(",").append("ERROR");
                            variableValues.get("gc").append(",").append("ERROR");
                            variableValues.get("mc").append(",").append("ERROR");
                            variableValues.get("sc").append(",").append("ERROR");
                            variableValues.get("it").append(",").append("ERROR");
                            variableValues.get("loc_t").append(",").append("ERROR");
                            variableValues.get("loc_p").append(",").append("ERROR");
                            variableValues.get("lt").append(",").append("ERROR");
                            variableValues.get("mg").append(",").append("ERROR");
                            variableValues.get("noc").append(",").append("ERROR");
                            variableValues.get("notc").append(",").append("ERROR");
                            variableValues.get("ro").append(",").append("ERROR");
                            variableValues.get("se").append(",").append("ERROR");
                            variableValues.get("st").append(",").append("ERROR");
                            variableValues.get("wmc_t").append(",").append("ERROR");
                            variableValues.get("wmc_p").append(",").append("ERROR");

                        }
                        LOGGER.info("Calculation of static factors completed");
                    }
                    
                    LOGGER.info("Calculating dependent variable");
                    variableValues.get("dormant").append(",").append(github.isDormant(untilDate));
                  
                    LOGGER.info("Writing files");

                    csvOutput.get("commits").println(projName + "" + variableValues.get("commits").toString());
                    csvOutput.get("contributors").println(projName + "" + variableValues.get("contributors").toString());
                    csvOutput.get("u_contributors").println(projName + "" + variableValues.get("u_contributors").toString());
                    csvOutput.get("c_contributors").println(projName + "" + variableValues.get("c_contributors").toString());
                    csvOutput.get("issues").println(projName + "" + variableValues.get("issues").toString());
                    csvOutput.get("non_dev_issues").println(projName + "" + variableValues.get("non_dev_issues").toString());
                    csvOutput.get("submitters").println(projName + "" + variableValues.get("submitters").toString());
                    csvOutput.get("non_dev_submitters").println(projName + "" + variableValues.get("non_dev_submitters").toString());
                    csvOutput.get("open_issues").println(projName + "" + variableValues.get("open_issues").toString());
                    csvOutput.get("non_dev_open_issues").println(projName + "" + variableValues.get("non_dev_open_issues").toString());
                    csvOutput.get("open_issues_submitters").println(projName + "" + variableValues.get("open_issues_submitters").toString());
                    csvOutput.get("non_dev_open_issues_submitters").println(projName + "" + variableValues.get("non_dev_open_issues_submitters").toString());
                    csvOutput.get("closed_issues").println(projName + "" + variableValues.get("closed_issues").toString());
                    csvOutput.get("non_dev_closed_issues").println(projName + "" + variableValues.get("non_dev_closed_issues").toString());
                    csvOutput.get("closed_issues_submitters").println(projName + "" + variableValues.get("closed_issues_submitters").toString());
                    csvOutput.get("non_dev_closed_issues_submitters").println(projName + "" + variableValues.get("non_dev_closed_issues_submitters").toString());
                    csvOutput.get("upstreams").println(projName + "" + variableValues.get("upstreams").toString());
                    csvOutput.get("downstreams").println(projName + "" + variableValues.get("downstreams").toString());
                    csvOutput.get("t_upstreams").println(projName + "" + variableValues.get("t_upstreams").toString());
                    csvOutput.get("t_downstreams").println(projName + "" + variableValues.get("t_downstreams").toString());
                    csvOutput.get("d_upstreams").println(projName + "" + variableValues.get("d_upstreams").toString());
                    csvOutput.get("q90").println(projName + "" + variableValues.get("q90").toString());
                    csvOutput.get("cc_degree").println(projName + "" + variableValues.get("cc_degree").toString());
                    csvOutput.get("dc_katz").println(projName + "" + variableValues.get("cc_degree").toString());
                    csvOutput.get("ar").println(projName + "" + variableValues.get("ar").toString());
                    csvOutput.get("dc").println(projName + "" + variableValues.get("dc").toString());
                    csvOutput.get("ec_t").println(projName + "" + variableValues.get("ec_t").toString());
                    csvOutput.get("ec_p").println(projName + "" + variableValues.get("ec_p").toString());
                    csvOutput.get("et").println(projName + "" + variableValues.get("et").toString());
                    csvOutput.get("fto").println(projName + "" + variableValues.get("fto").toString());
                    csvOutput.get("cdsbp").println(projName + "" + variableValues.get("cdsbp").toString());
                    csvOutput.get("cc").println(projName + "" + variableValues.get("cc").toString());
                    csvOutput.get("fd").println(projName + "" + variableValues.get("fd").toString());
                    csvOutput.get("gc").println(projName + "" + variableValues.get("gc").toString());
                    csvOutput.get("mc").println(projName + "" + variableValues.get("mc").toString());
                    csvOutput.get("sc").println(projName + "" + variableValues.get("sc").toString());
                    csvOutput.get("it").println(projName + "" + variableValues.get("it").toString());
                    csvOutput.get("loc_t").println(projName + "" + variableValues.get("loc_t").toString());
                    csvOutput.get("loc_p").println(projName + "" + variableValues.get("loc_p").toString());
                    csvOutput.get("lt").println(projName + "" + variableValues.get("lt").toString());
                    csvOutput.get("mg").println(projName + "" + variableValues.get("mg").toString());
                    csvOutput.get("noc").println(projName + "" + variableValues.get("noc").toString());
                    csvOutput.get("notc").println(projName + "" + variableValues.get("notc").toString());
                    csvOutput.get("ro").println(projName + "" + variableValues.get("ro").toString());
                    csvOutput.get("se").println(projName + "" + variableValues.get("se").toString());
                    csvOutput.get("st").println(projName + "" + variableValues.get("st").toString());
                    csvOutput.get("wmc_t").println(projName + "" + variableValues.get("wmc_t").toString());
                    csvOutput.get("wmc_p").println(projName + "" + variableValues.get("wmc_p").toString());

                    csvOutput.get("dormant").println(projName + "" + variableValues.get("dormant").toString());
                    deleteDirectory(cloneDir);

                    // mark job as completed and get next job to execute
                    restTemplate.postForLocation(jobApiBaseUrl + Utils.POST_DONE_JOB_ENDPOINT, job.getId());
                    LOGGER.info("Marked the job as done. id = {}, project = {}, and github link = {}", job.getId(), projName, cloneLink);
                    job = restTemplate.getForObject(jobApiBaseUrl + Utils.GET_JOB_TO_DO_ENDPOINT, Job.class);
                } catch (Exception e){
                    // GENERAL FAILURE
                    LOGGER.error("Error in processing project {} from {}", projName, cloneLink);
                    LOGGER.error(e.getMessage());

                    // mark job as failed and get next job to execute
                    restTemplate.postForLocation(jobApiBaseUrl + Utils.POST_FAILED_JOB_ENDPOINT, job.getId());
                    LOGGER.info("Marked the job as failed. id = {}, project = {}, and github link = {}", job.getId(), projName, cloneLink);
                    job = restTemplate.getForObject(jobApiBaseUrl + Utils.GET_JOB_TO_DO_ENDPOINT, Job.class);
                }
            }
        } finally {
            for (PrintWriter printWriter : csvOutput.values()) {
                printWriter.close();
            }
        }
        LOGGER.info("Completed writing files");
    }

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

}
