package ch.uzh.testsonsustainability;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.FileSystems;
import java.time.LocalDate;
import java.time.Month;

import static org.junit.Assert.*;

public class MavenCentralInfoTest {

    public static final String TEST_CSV_PATH = "testResources/";
    public MavenCentralInfo mavenCentralInfo;
    public String artifact1;
    public String tag1;
    public String artifactTag2;

    @Before
    public void setUp() throws Exception {
        artifact1 = "com.hankcs:hanlp";
        tag1 = "portable-1.5.0";
        artifactTag2 = "org.fake:tag";
        String absolutePath = FileSystems.getDefault().getPath(TEST_CSV_PATH).normalize().toAbsolutePath().toString();
        mavenCentralInfo = new MavenCentralInfo(Utils.normalizePath(absolutePath));
    }

    @Test
    public void testArtifactTagExists(){
        assertEquals(Boolean.TRUE,mavenCentralInfo.artifactTagExists(artifact1+":"+tag1));
        assertEquals(Boolean.FALSE,mavenCentralInfo.artifactTagExists(artifactTag2));
    }


    @Test
    public void testGetCurrentVersionArtifactTag() throws Exception{
        assertEquals(artifact1+":portable-1.5.0",mavenCentralInfo.getCurrentVersionArtifactTag(artifact1, LocalDate.of(2018, Month.JANUARY,1)));
    }

    @Test
    public void testGetDependencies(){
        assertEquals(0,mavenCentralInfo.getDependencies(artifact1+":"+tag1).size());
    }

    @Test
    public void testGetDependants(){
        assertEquals(1,mavenCentralInfo.getDependants(artifact1+":"+tag1).size());
    }

    @Test
    public void testGetTransitiveDependencies(){
        assertEquals(0,mavenCentralInfo.getTransitiveDependencies(artifact1+":"+tag1).size());
    }

    @Test
    public void testGetTransitiveDependants(){
        assertEquals(2,mavenCentralInfo.getTransitiveDependants(artifact1+":"+tag1).size());
    }

    @After
    public void tearDown() throws Exception {
    }
}
