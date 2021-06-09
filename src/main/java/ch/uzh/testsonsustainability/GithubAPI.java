package ch.uzh.testsonsustainability;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import exception.IssueStateNotValidException;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.ketch.LogIndex;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GithubAPI {

    private static Logger LOGGER = LoggerFactory.getLogger(GithubAPI.class);

    private final GitHub github;

    private final GHRepository repo;
    private final List<GHCommit> commits;
    private final List<GHIssue> closedIssues;
    private final List<GHIssue> openIssues;
    private final List<GHIssue> allIssues;
    private final List<GHRepository.Contributor> contributors;
    private final List<GHTag> tags;

    private final List<String> universityDomains;
    private final List<String> publicDomains;

    public GithubAPI(String repoName, Domains domains, int startingYear, Git git, Pair<String, String> ghUserPassword) throws IOException {
        LOGGER.debug("Connecting to github with user {}", ghUserPassword.getLeft());
        github = GitHub.connect(ghUserPassword.getLeft(), ghUserPassword.getRight());
        Date startingDate = new Date(startingYear - 1900, 0, 1);
        Date endingDate = new Date();
        LOGGER.debug("Retrieving information for repository {}", repoName);
        repo = github.getRepository(repoName);
        commits = removeMergePullRequestCommit(repo.queryCommits().since(startingDate).until(endingDate).list().asList(),git);
        LOGGER.debug("Retrieved {} commits", commits.size());
        closedIssues = repo.listIssues(GHIssueState.CLOSED).asList().stream().filter(issue -> !issue.isPullRequest()).collect(Collectors.toList());
        LOGGER.debug("Retrieved {} closed issues", closedIssues.size());
        openIssues = repo.listIssues(GHIssueState.OPEN).asList().stream().filter(issue -> !issue.isPullRequest()).collect(Collectors.toList());
        LOGGER.debug("Retrieved {} open issues", openIssues.size());
        allIssues = repo.listIssues(GHIssueState.ALL).asList().stream().filter(issue -> !issue.isPullRequest()).collect(Collectors.toList());
        LOGGER.debug("Retrieved {} all issues", allIssues.size());
        contributors = repo.listContributors().asList();
        LOGGER.debug("Retrieved {} contributors", contributors.size());
        tags = repo.listTags().asList();
        LOGGER.debug("Retrieved {} tags", tags.size());
        universityDomains = domains.universityDomains;
        publicDomains = domains.publicDomains;
        LOGGER.debug("Retrieval completed");
    }

    private List<GHCommit> removeMergePullRequestCommit(List<GHCommit> commits, Git git) throws IOException{
        List<GHCommit> filteredCommits = new ArrayList<>();
        filteredCommits.addAll(commits);
        for (GHCommit commit: commits){
            if (commit.getParents().size() > 1){
                GHCommit mostRecentParentCommit = null;
                for (GHCommit parentCommit : commit.getParents()){
                    if (mostRecentParentCommit == null || parentCommit.getCommitDate().after(mostRecentParentCommit.getCommitDate())){
                        mostRecentParentCommit = parentCommit;
                    }
                }
                ObjectReader reader = git.getRepository().newObjectReader();
                RevCommit oldCommit = git.getRepository().parseCommit(LogIndex.fromString(mostRecentParentCommit.getSHA1()));
                RevCommit newCommit = git.getRepository().parseCommit(LogIndex.fromString(commit.getSHA1()));
                ObjectId oldTree = oldCommit.getTree();
                ObjectId newTree = newCommit.getTree();
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset( reader, oldTree );
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, newTree);
                DiffFormatter df = new DiffFormatter( new ByteArrayOutputStream() ); // use NullOutputStream.INSTANCE if you don't need the diff output
                df.setRepository( git.getRepository() );
                List<DiffEntry> entries = df.scan( oldTreeIter, newTreeIter );
                if (entries.isEmpty()){
                    filteredCommits.remove(commit);
                    LOGGER.info("FOUND A MERGE PULL REQUEST COMMIT " +commit.getSHA1());
                }
            }
        }
        return filteredCommits;
    }

    public int getNumberOfCommits(Date since, Date until) throws IOException {
        LOGGER.debug("Retrieving number of commits from GH API");
        List<GHCommit> monthlyCommits = getCommits(since, until);
        LOGGER.debug("Number of commits retrieved");
        return monthlyCommits.size();
    }

    private List<GHCommit> getCommits(Date since, Date until) throws IOException {
        List<GHCommit> monthlyCommits = new ArrayList<>();
        for (GHCommit c : commits) {
            if (c.getCommitDate().after(since) && c.getCommitDate().before(until))
                monthlyCommits.add(c);
        }
        return monthlyCommits;
    }

    public Set<String> getContributors(Date since, Date until) throws IOException {
        LOGGER.debug("Retrieving number of contributors from GH API");
        Set<String> contributorsEmails = Sets.newHashSet();
        List<GHCommit> monthlyCommits = getCommits(since, until);
        for (GHCommit c : monthlyCommits) {
            contributorsEmails.add(c.getCommitShortInfo().getAuthor().getEmail());
        }
        LOGGER.debug("Number of contributors retrieved");
        return contributorsEmails;
    }

    public int getNumberOfContributors(Date since, Date until) throws IOException {
        return getContributors(since, until).size();
    }

    public int getNumberOfIssues(GHIssueState state, Date since, Date until)
            throws IOException, IssueStateNotValidException{
        LOGGER.debug("Retrieving number of issues from GH API");
        List<GHIssue> monthlyIssues = getIssues(state, since, until);
        LOGGER.debug("Number of issues retrieved");
        return monthlyIssues.size();
    }

    private List<GHIssue> getIssuesByState(GHIssueState state) throws IssueStateNotValidException{
        if (state.equals(GHIssueState.CLOSED)){
            return closedIssues;
        }
        else if (state.equals(GHIssueState.OPEN)){
            return openIssues;
        }
        else if (state.equals(GHIssueState.ALL)){
            return allIssues;
        }

        throw new IssueStateNotValidException("State is not valid");
    }

    private List<GHIssue> getIssues(GHIssueState state, Date since, Date until)
            throws IOException, IssueStateNotValidException {
        List<GHIssue> monthlyIssues = new ArrayList<>();
        for (GHIssue i : getIssuesByState(state)) {
            if (i.getCreatedAt().after(since) && i.getCreatedAt().before(until))
                monthlyIssues.add(i);
        }
        return monthlyIssues;
    }


    public int getNumberOfSubmitters(GHIssueState state, Date since, Date until) throws IOException, IssueStateNotValidException {
        return getSubmitters(state, since, until).size();
    }

    public Set<String> getSubmitters(GHIssueState state, Date since, Date until) throws IOException, IssueStateNotValidException {
        LOGGER.debug("Retrieving number of submitters from GH API");
        Set<String> submitters = Sets.newHashSet();
        List<GHIssue> monthlyIssues = getIssues(state, since, until);
        for (GHIssue issue : monthlyIssues) {
            submitters.add(issue.getUser().getLogin());
        }
        LOGGER.debug("Number of submitters retrieved");
        return submitters;
    }

    public int getNumberOfNonDevIssues(GHIssueState state, Date since, Date until) throws IOException, IssueStateNotValidException {
        LOGGER.debug("Retrieving number of non dev issues from GH API");
        Set<String> collaboratorNames = getCollaboratorNames();
        int nonDevIssues = 0;
        for (GHIssue issue : getIssues(state, since, until)){
            if (!collaboratorNames.contains(issue.getUser().getLogin())) {
                nonDevIssues++;
            }
        }
        LOGGER.debug("Number of non dev issues retrieved");
        return nonDevIssues;
    }

    private Set<String> getCollaboratorNames() {
        Set<String> collaboratorNames = Sets.newHashSet();
        for (GHRepository.Contributor contributor : contributors) {
            collaboratorNames.add(contributor.getLogin());
        }
        return collaboratorNames;
    }

    public long getNumberOfNonDevSubmitters(GHIssueState state, Date since, Date until) throws IOException, IssueStateNotValidException {
        LOGGER.debug("Retrieving number of non dev submitters from GH API");
        Set<String> collaboratorNames = getCollaboratorNames();
        return getSubmitters(state, since, until).stream()
                .filter( submitter -> !collaboratorNames.contains(submitter))
                .count();
    }

    public boolean isDormant(Date until) throws IOException {
        LOGGER.debug("Checking if a project is dormant at date {}", until);
        Date since = new Date(until.getYear() - 1, until.getMonth(), 1);
        int n_commits = getNumberOfCommits(since, until);
        return (n_commits < 12);
    }

    public String getBranchAtDate(Date until) throws IOException {
        LOGGER.debug("Retrieving branches at date {} from GH API", until);
        Date currentDate = null;
        String tag = null;
        for (GHTag t : tags) {
            Date commitDate = t.getCommit().getCommitDate();
            if (currentDate == null || commitDate.after(currentDate) && commitDate.before(until)) {
                currentDate = commitDate;
                tag = t.getName();
            }
        }
        LOGGER.debug("Branches at date {} retrieved", until);
        return tag;
    }

    public int getSizeOfTheCoreTeam(Date since, Date until) throws IOException {
        LOGGER.info("Calculating size of the core team");
        Multiset<String> committers = HashMultiset.create();
        int numberOfCommits = 0;
        List<GHCommit> monthlyCommits = getCommits(since, until);
        for (GHCommit c : monthlyCommits) {
            if (c.getCommitter()!= null) {
                String email = c.getCommitter().getEmail();
                if (email != null) {
                    committers.add(email);
                }
            }
            numberOfCommits++;
        }
        Set<String> keyset = committers.elementSet();
        String maxKey = "";
        int max = 0;
        int sum = 0;
        double covered = 0;
        int q90 = 0;
        while (covered < 90 && !committers.isEmpty()) {
            for (String s : keyset) {
                if (committers.count(s) > max) {
                    max = committers.count(s);
                    maxKey = s;
                }
            }
            sum += committers.count(maxKey);
            committers.remove(maxKey);
            q90++;
            covered = (sum / numberOfCommits) * 100;
            max = 0;
        }
        if (covered < 90) {
            q90 = 0;
        }
        LOGGER.info("Size of the core team calculated");
        return q90;
    }

    public int getSocialTies(Date since, Date until) throws IOException {
        LOGGER.info("Calculating social ties");
        ArrayList<String> repoNames = new ArrayList<String>();
        List<GHCommit> monthlyCommits = getCommits(since, until);
        for (GHCommit c : monthlyCommits) {
            GHUser author = c.getAuthor();
            if (author != null) {
                String login = author.getLogin();
                List<GHRepository> repos = author.listSubscriptions().asList();
                for (GHRepository repo : repos) {
                    try {
                        List<GHCommit> dev_commits = repo.queryCommits().author(login).since(since).until(until).list()
                                .asList();
                        if (!dev_commits.isEmpty()) {
                            if (!repoNames.contains(repo.getUrl().getPath())) {
                                repoNames.add(repo.getUrl().getPath());
                            }
                        }
                    } catch (GHException e) {
                        LOGGER.error("Repository {} is empty", repo);
                        LOGGER.error(e.getMessage());
                    }
                }
            }
        }
        LOGGER.info("Social ties calculated");
        return repoNames.size();
    }


    public int getNumberOfUniversityContributors(Date since, Date until) throws IOException {
        LOGGER.debug("Retrieving number of university contributors");
        int universityContributors = 0;
        List<GHCommit> monthlyCommits = getCommits(since,until);

        for (GHCommit c : monthlyCommits) {
            if (c.getCommitShortInfo().getAuthor() != null) {
                String email = c.getCommitShortInfo().getAuthor().getEmail();
                if (email != null && email.contains("@")) {
                    String domain = email.split("@")[1];
                    if (universityDomains.contains(domain))
                        universityContributors++;
                }
            }
        }
        LOGGER.debug("Number of university contributors retrieved");
        return universityContributors;
    }

    public int getNumberOfCommercialContributors(Date since, Date until) throws IOException {
        LOGGER.debug("Retrieving number of commercial contributors");
        int commercialContributors = 0;
        List<GHCommit> monthlyCommits = getCommits(since,until);
        for (GHCommit c : monthlyCommits) {
            if (c.getCommitShortInfo().getAuthor() != null) {
                String email = c.getCommitShortInfo().getAuthor().getEmail();
                String domain = email.split("@")[1];
                if (!universityDomains.contains(domain) && !publicDomains.contains(domain))
                    commercialContributors++;
            }
        }
        LOGGER.debug("Number of commercial contributors retrieved");
        return commercialContributors;
    }
}
