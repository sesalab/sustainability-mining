package ch.uzh.testsonsustainability;


import com.google.common.collect.ImmutableMap;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Utils {

    public static final String APPLICATION_NAME = "tests-on-sustainability";
    public static final String CSV_INPUT_FILENAME = "githubs_sorted_for_stars.csv";
    public static final String CSV_MAVEN_LINKS_FILENAME = "links_all.csv";
    public static final String ALL_ARTIFACT_RELEASES_FILENAME = "release_all.csv";
    public static final String GET_JOB_TO_DO_ENDPOINT = "/jobs/get-job-to-do";
    public static final String POST_DONE_JOB_ENDPOINT = "/jobs/job-done";
    public static final String POST_CANCEL_JOB_ENDPOINT = "/jobs/cancel-job";
    public static final String POST_FAILED_JOB_ENDPOINT = "/jobs/job-failed";
    public static final String GET_JOBS_TO_DO_ENDPOINT = "/jobs/get-jobs-to-do";
    public static final String GET_DOING_JOBS_ENDPOINT = "/jobs/get-doing-jobs";
    public static final String GET_DONE_JOBS_ENDPOINT = "/jobs/get-done-jobs";
    public static final String GET_FAILED_JOBS_ENDPOINT = "/jobs/get-failed-jobs";


    public static String normalizePath(String path) {
        if (path.endsWith("/")) {
            return path;
        } else {
            return path + "/";
        }
    }

    private static PrintWriter createPrintWriter(String path) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(path, true));
        return new PrintWriter(bufferedWriter, true);
    }

    public static ImmutableMap<String, PrintWriter> csvOutput(String outPath) throws IOException {
        return ImmutableMap.<String, PrintWriter>builder()
                .put("commits", createPrintWriter(outPath + "/commits.csv"))
                .put("contributors", createPrintWriter(outPath + "/contributors.csv"))
                .put("u_contributors", createPrintWriter(outPath + "/university.csv"))
                .put("c_contributors", createPrintWriter(outPath + "/commercial.csv"))
                .put("open_issues", createPrintWriter(outPath + "/open_issues.csv"))
                .put("non_dev_open_issues", createPrintWriter(outPath + "/non_dev_open_issues.csv"))
                .put("open_issues_submitters", createPrintWriter(outPath + "/open_issues_submitters.csv"))
                .put("non_dev_open_issues_submitters", createPrintWriter(outPath + "/non_dev_open_issues_submitters.csv"))
                .put("closed_issues", createPrintWriter(outPath + "/closed_issues.csv"))
                .put("non_dev_closed_issues", createPrintWriter(outPath + "/non_dev_closed_issues.csv"))
                .put("closed_issues_submitters", createPrintWriter(outPath + "/closed_issues_submitters.csv"))
                .put("non_dev_closed_issues_submitters", createPrintWriter(outPath + "/non_dev_closed_issues_submitters.csv"))
                .put("issues", createPrintWriter(outPath + "/issues.csv"))
                .put("non_dev_issues", createPrintWriter(outPath + "/non_dev_issues.csv"))
                .put("submitters", createPrintWriter(outPath + "/issues_submitters.csv"))
                .put("non_dev_submitters", createPrintWriter(outPath + "/non_dev_issues_submitters.csv"))
                .put("upstreams", createPrintWriter(outPath + "/upstreams.csv"))
                .put("downstreams", createPrintWriter(outPath + "/downstreams.csv"))
                .put("t_upstreams", createPrintWriter(outPath + "/t_upstreams.csv"))
                .put("t_downstreams", createPrintWriter(outPath + "/t_downstreams.csv"))
                .put("d_upstreams", createPrintWriter(outPath + "/d_upstreams.csv"))
                .put("q90", createPrintWriter(outPath + "/q90.csv"))
                .put("cc_degree", createPrintWriter(outPath + "/cc_degree.csv"))
                .put("dc_katz", createPrintWriter(outPath + "/dc_katz.csv"))
                .put("ar", createPrintWriter(outPath + "/assertion_roulette.csv"))
                .put("dc", createPrintWriter(outPath + "/duplicated_code.csv"))
                .put("ec_t", createPrintWriter(outPath + "/efferent_coupling_test.csv"))
                .put("ec_p", createPrintWriter(outPath + "/efferent_coupling_production.csv"))
                .put("et", createPrintWriter(outPath + "/eager_test.csv"))
                .put("fto", createPrintWriter(outPath + "/for_testers_only.csv"))
                .put("cdsbp", createPrintWriter(outPath + "/class_data_should_be_private.csv"))
                .put("cc", createPrintWriter(outPath + "/complex_class.csv"))
                .put("fd", createPrintWriter(outPath + "/functional_decomposition.csv"))
                .put("gc", createPrintWriter(outPath + "/god_class.csv"))
                .put("mc", createPrintWriter(outPath + "/misplaced_class.csv"))
                .put("sc", createPrintWriter(outPath + "/spaghetti_code.csv"))
                .put("it", createPrintWriter(outPath + "/indirect_testing.csv"))
                .put("loc_t", createPrintWriter(outPath + "/loc_test.csv"))
                .put("loc_p", createPrintWriter(outPath + "/loc_production.csv"))
                .put("lt", createPrintWriter(outPath + "/lazy_test.csv"))
                .put("mg", createPrintWriter(outPath + "/mystery_guest.csv"))
                .put("noc", createPrintWriter(outPath + "/number_of_classes.csv"))
                .put("notc", createPrintWriter(outPath + "/number_of_test_classes.csv"))
                .put("ro", createPrintWriter(outPath + "/resource_optimism.csv"))
                .put("se", createPrintWriter(outPath + "/sensitive_equality.csv"))
                .put("st", createPrintWriter(outPath + "/smelly_tests.csv"))
                .put("wmc_t", createPrintWriter(outPath + "/wmc_test.csv"))
                .put("wmc_p", createPrintWriter(outPath + "/wmc_production.csv"))
                .put("dormant", createPrintWriter(outPath + "/dormant.csv"))
                .build();
    }
}
