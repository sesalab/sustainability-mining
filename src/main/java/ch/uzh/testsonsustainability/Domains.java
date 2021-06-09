package ch.uzh.testsonsustainability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Domains {

    private static Logger LOGGER = LoggerFactory.getLogger(Domains.class);
    private final String PUBLIC_DOMAIN_FILENAME = "publicDomains.txt";
    private final String UNIVERSITY_DOMAIN_FILENAME = "universityDomains.txt";
    public final List<String> universityDomains;
    public final List<String> publicDomains;

    public Domains(String csvPath) throws IOException{
        LOGGER.info("Reading university domain from file {}", UNIVERSITY_DOMAIN_FILENAME);
        universityDomains = retrieveDomains(csvPath + "/" + UNIVERSITY_DOMAIN_FILENAME);
        LOGGER.info("Found {} university domains", universityDomains.size());
        LOGGER.info("Reading public domain from file {}", PUBLIC_DOMAIN_FILENAME);
        publicDomains = retrieveDomains(csvPath + "/" + PUBLIC_DOMAIN_FILENAME);
        LOGGER.info("Found {} public domains", publicDomains.size());
    }
/*
    private List<String> retrieveUniversityDomains() throws IOException {
        LOGGER.info("Reading university domains from remote url");
        List<String> u_domains = new ArrayList<String>();
        String apiURL = "https://raw.githubusercontent.com/Hipo/university-domains-list/master/world_universities_and_domains.json";
        String command = "curl -u "+Utils.GithubAccesKey.nickname+":"+Utils.GithubAccesKey.accessToken+" " + apiURL;
        Process process = Runtime.getRuntime().exec(command);
        BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String s;
        String json = "";
        while ((s = stdOut.readLine()) != null) {
            json += s;
        }
        JSONArray universities = new JSONArray(json);
        for (int i = 0; i < universities.length(); i++) {
            JSONArray domains = universities.getJSONObject(i).getJSONArray("domains");
            for (int j = 0; j < domains.length(); j++) {
                u_domains.add(domains.getString(j));
            }
        }

        LOGGER.info("Found {} university domains", u_domains.size());
        return u_domains;
    }
    */

    private List<String> retrieveDomains(String domainsPath) throws IOException {
        List<String> domains = new ArrayList<String>();
        BufferedReader br = new BufferedReader(new FileReader(domainsPath));
        String s;
        while ((s = br.readLine()) != null) {
            domains.add(s.trim());
        }
        return domains;
    }
}
