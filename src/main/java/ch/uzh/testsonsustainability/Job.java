package ch.uzh.testsonsustainability;

public class Job {

    private String project;
    private String githubLink;
    private String id;

    public Job() {
    }

    public void setProject(String project) {
        this.project = project;
    }

    public void setGithubLink(String githubLink) {
        this.githubLink = githubLink;
    }

    public void setId(String id) {
        this.id = id;
    }


    public String getProject() {
        return project;
    }

    public String getGithubLink() {
        return githubLink;
    }

    public String getId() { return id; }

    @Override
    public String toString() {
        return id;
    }

}

