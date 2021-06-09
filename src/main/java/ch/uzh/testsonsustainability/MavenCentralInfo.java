package ch.uzh.testsonsustainability;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import edu.stanford.nlp.util.ArraySet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class MavenCentralInfo {

	private static Logger LOGGER = LoggerFactory.getLogger(MavenCentralInfo.class);

	private final Graph<String> mavenGraph;
	private final Graph<String> mavenInvertedGraph;
	private final SortedSetMultimap<String, Pair<String, LocalDate>> artifactReleases;
	private final Map<String, String> artifactGHRepoMap;

	public MavenCentralInfo(String csvPath) throws IOException {
		ImmutableGraph.Builder<String> mavenGraphBuilder = GraphBuilder
				.directed()
				.allowsSelfLoops(false)
				.immutable();

		LOGGER.info("Parsing maven links from file {} and building graphs in memory", csvPath + Utils.CSV_MAVEN_LINKS_FILENAME);
		BufferedReader brLinks = new BufferedReader(new FileReader(csvPath + Utils.CSV_MAVEN_LINKS_FILENAME));
		CSVParser linksParser = CSVParser.parse(brLinks, CSVFormat.DEFAULT);

		for (CSVRecord csvRecord : linksParser) {
			String fromNodeLabel = csvRecord.get(0);
			String toNodeLabel = csvRecord.get(1);
			try {
				mavenGraphBuilder.putEdge(fromNodeLabel, toNodeLabel);
			} catch (Exception e) {
				LOGGER.error("Error when trying to add link {} => {}", fromNodeLabel, toNodeLabel);
				LOGGER.error(e.getMessage());
			}
		}
		mavenGraph = mavenGraphBuilder.build();
		mavenInvertedGraph = Graphs.transpose(mavenGraph);
		LOGGER.info("Maven graphs built");

		// keep sorted for performance on lookup - keeping most recent last
		Comparator<Pair<String, LocalDate>> tagDateComparator = Comparator.comparing(Pair::getRight, Comparator.reverseOrder());
		Comparator<String> artifactComparator = Comparator.naturalOrder();
		artifactReleases = TreeMultimap.create(artifactComparator, tagDateComparator);

		LOGGER.info("Parsing release from file {} and building map in memory", csvPath + Utils.ALL_ARTIFACT_RELEASES_FILENAME);
		BufferedReader brReleases = new BufferedReader(new FileReader(csvPath + Utils.ALL_ARTIFACT_RELEASES_FILENAME));
		CSVParser releasesParser = CSVParser.parse(brReleases, CSVFormat.DEFAULT.withFirstRecordAsHeader());
		for (CSVRecord csvRecord : releasesParser) {
			String[] artifactTag = csvRecord.get("artifact").split(":");
			String artifact = String.format("%s:%s", artifactTag[0], artifactTag[1]);
			String tag = artifactTag[2];
			LocalDateTime releaseDateTime = LocalDateTime.parse(csvRecord.get("release"), DateTimeFormatter.ISO_ZONED_DATE_TIME);
			artifactReleases.put(artifact, ImmutablePair.of(tag, releaseDateTime.toLocalDate()));
		}
		LOGGER.info("Releases imported");

		ImmutableMap.Builder artifactGHRepoMapBuilder = new ImmutableMap.Builder();
		LOGGER.info("Parsing artifacts / GH repos from file {} and building map in memory", csvPath + Utils.CSV_INPUT_FILENAME);
		BufferedReader brArtifactGHRepo = new BufferedReader(new FileReader(csvPath + Utils.CSV_INPUT_FILENAME));
		CSVParser artifactGHRepoParser = CSVParser.parse(brArtifactGHRepo, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
		for (CSVRecord csvRecord : artifactGHRepoParser) {
			String artifact = csvRecord.get("Project");
			String ghRepo = csvRecord.get("Github Link");
			artifactGHRepoMapBuilder.put(artifact, ghRepo);
		}
		artifactGHRepoMap = artifactGHRepoMapBuilder.build();
		LOGGER.info("Artifacts / GH repos imported");
	}

	public Boolean artifactTagExists(String artifactTag) {
		return mavenGraph.nodes().contains(artifactTag);
	}

	private String artifactWithTag(String artifact, String tag) {
		return String.format("%s:%s", artifact, tag);
	}

	public String getCurrentVersionArtifactTag(String artifact, LocalDate date) throws Exception {
		String tag = getCurrentVersionTag(artifact, date);
		return artifactWithTag(artifact, tag);
	}


	private String getCurrentVersionTag(String artifact, LocalDate date) throws Exception {
		if (!artifactReleases.containsKey(artifact)){
			LOGGER.error("Artifact {} not found in artifact list", artifact);
			throw new Exception(String.format("Artifact {} not found in artifact list", artifact));
		}

		LocalDate threshold = date.plusMonths(1);
		SortedSet<Pair<String, LocalDate>> artifactTagsDates = artifactReleases.get(artifact);

		// if most recent release if before the threshold return that one
		if (!artifactTagsDates.first().getRight().isAfter(threshold)) {
			return artifactTagsDates.first().getLeft();
		} else {
			// else, traverse the versions from newest to oldest and return the first that is before threshold
			SortedSet<Pair<String, LocalDate>> tailsSet = artifactTagsDates.tailSet(ImmutablePair.of("", threshold));
			// temporary to check is my understanding of tailset is correct
			if (tailsSet.first().getRight().isAfter(threshold)) {
				LOGGER.error("Something wrong with the tail set - investigate");
			}
			if (tailsSet.isEmpty()){
				// no version before threshold, we return null to signal that
				return null;
			} else {
				// newest version before the threshold
				return tailsSet.first().getLeft();
			}
		}
	}

	public Set<String> getDependencies(String artifactTag){
		return mavenGraph.successors(artifactTag);
	}

	public Set<String> getDependants(String artifactTag) /*throws Exception*/ {
		/*if (mavenInvertedGraph.successors(artifactTag).size() != mavenGraph.predecessors(artifactTag).size()) {
			LOGGER.error("Something is wrong about the inverted graph - please investigate");
			throw new Exception("Something is wrong about the inverted graph - please investigate");
		}*/
		return mavenGraph.predecessors(artifactTag);
	}

	public Set<String> getTransitiveDependencies(String artifactTag) {
		return Graphs.reachableNodes(mavenGraph, artifactTag).stream()
				.filter(dependencyArtifactTag -> !dependencyArtifactTag.equals(artifactTag))
				.collect(Collectors.toSet());
	}

	public Set<String> getTransitiveDependants(String artifactTag) {
		return Graphs.reachableNodes(mavenInvertedGraph, artifactTag).stream()
				.filter(dependencyArtifactTag -> !dependencyArtifactTag.equals(artifactTag))
				.collect(Collectors.toSet());
	}

	public Set<String> getDormantDependencies(String artifactTag, LocalDate date, GithubAPI githubAPI) {
		Set<String> dependencies = mavenGraph.successors(artifactTag);
		return dependencies.stream()
				.filter( dependencyArtifactTag -> isGHRepoDormant(dependencyArtifactTag, date, githubAPI))
				.collect(Collectors.toSet());
	}

	private boolean isGHRepoDormant(String artifactTag, LocalDate date, GithubAPI githubAPI) {
		String[] artifactTagSplits = artifactTag.split(":");
		String artifact = String.format("%s:%s", artifactTagSplits[0], artifactTagSplits[1]);
		if (!artifactGHRepoMap.containsKey(artifact)) {
			// if we do not have the GH repo link, we cannot establish if the project is dormant => convention is it is not dormant
			return false;
		} else {
			String ghRepo = artifactGHRepoMap.get(artifact);
			try {
				return githubAPI.isDormant(new Date(date.getYear() - 1900, date.getMonthValue()-1, date.getDayOfMonth()));
			} catch (Exception e) {
				LOGGER.error("Error when establishing if repo {} is dormant", ghRepo);
				LOGGER.error(e.getMessage());
				// we cannot establish if the project is dormant => convention is it is not dormant
				return false;
			}
		}
	}


//	private int[] getNumberOfUpAndDownstreams(List<String[]> map, String key,
//													 List<String> u_analyzedKeys, List<String> d_analyzedKeys, String csvPath, String date)
//			throws IOException, InterruptedException {
//		LOGGER.debug("Calculating number of up/downstreams for {} at date {}", key, date);
//		int[] upAndDown = new int[5];
//		upAndDown[0] = 0;
//		upAndDown[1] = 0;
//		upAndDown[2] = 0;
//		upAndDown[3] = 0;
//		upAndDown[4] = 0;
//		for (String[] entry : map) {
//			String from = entry[0];
//			String to = entry[1];
//			if (from.equals(key)) {
//				upAndDown[0]++;
//				upAndDown[2]++;
//				if (!u_analyzedKeys.contains(to)) {
//					u_analyzedKeys.add(to);
//					upAndDown[2] += getNumberOfUpAndDownstreams(map, to, u_analyzedKeys, d_analyzedKeys, csvPath,
//							date)[2];
//				}
//				String githubLink = artifactGHRepoMap.get(to);
//				if (!githubLink.equals("NOT FOUND")) {
//					int y = Integer.parseInt(date.substring(0,4));
//					int m = Integer.parseInt(date.substring(5,7));
//					if (GithubAPI.isDormant(githubLink.split("github.com/")[1], y,m))
//						upAndDown[4]++;
//				}
//			} else if (to.equals(key)) {
//				upAndDown[1]++;
//				upAndDown[3]++;
//				if (!d_analyzedKeys.contains(from)) {
//					d_analyzedKeys.add(from);
//					upAndDown[3] += getNumberOfUpAndDownstreams(map, from, u_analyzedKeys, d_analyzedKeys, csvPath,
//							date)[3];
//				}
//			}
//		}
//		LOGGER.debug("Number of up/downstreams calculated");
//		return upAndDown;
//	}


	public double katzCentrality(String artifact, LocalDate date, String csvPath) throws Exception {
		LOGGER.info("Calculating katz centrality for {} at date {}", artifact, date);

		String csvFileLinksPath = csvPath + Utils.CSV_MAVEN_LINKS_FILENAME;
		BufferedReader br = new BufferedReader(new FileReader(csvFileLinksPath));

		double kc = 0;
		double x1 = 0;
		double a = 0.5;
		int weight = 1;
		double init_vertex_cent = 0.37;

		ArrayList<String> analyzed = new ArrayList<String>();
		ArrayList<String> elements = new ArrayList<String>();
		String str;
		int i = 0;
		while ((str = br.readLine()) != null) {
			String[] splitted = str.split(",");

			String from = splitted[0].substring(1, splitted[0].length() - 1);
			if (!elements.contains(from))
				elements.add(from);

			String to = splitted[1].substring(1, splitted[1].length() - 1);
			if (!elements.contains(to))
				elements.add(to);

			if (artifact.equals(from) && !analyzed.contains(to)) {
				x1 = (a * 1 * init_vertex_cent) + weight;
				kc = kc + (x1 * x1);
				analyzed.add(to);
			}
		}

		br.close();
		for (String s : elements) {
			if (!analyzed.contains(s)) {
				x1 = (a * 0 * init_vertex_cent) + weight;
				kc = kc + (x1 * x1);
				analyzed.add(s);
			}
		}
		LOGGER.info("Katz centrality computed", artifact, date);
		return kc;
	}

	public double katzCentrality(String artifact, LocalDate date) throws Exception {
		LOGGER.info("Calculating katz centrality for {} at date {}", artifact, date);

		String artifactTag = getCurrentVersionArtifactTag(artifact, date);

		double kc = 0;
		double x1 = 0;
		double a = 0.5;
		int weight = 1;
		double init_vertex_cent = 0.37;

		Set<String> successors = mavenGraph.successors(artifactTag);

		for (String successorNode : successors) {
			x1 = (a * 1 * init_vertex_cent) + weight;
			kc = kc + (x1 * x1);
		}

		Set<String> allButSuccessors = Sets.difference(mavenGraph.nodes(), successors);
		for (String node : allButSuccessors) {
			//x1 = (a * 0 * init_vertex_cent) + weight;
			x1 = weight;
			kc = kc + (x1 * x1);
		}

		LOGGER.info("Katz centrality computed", artifact, date);
		return kc;
	}
}
