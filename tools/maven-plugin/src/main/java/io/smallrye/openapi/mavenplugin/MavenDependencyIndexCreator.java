package io.smallrye.openapi.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.Result;

@Component(role = MavenDependencyIndexCreator.class, instantiationStrategy = "singleton")
public class MavenDependencyIndexCreator {

    private static final Set<String> IGNORED_GROUPIDS = new HashSet<>();
    private static final Set<String> IGNORED_GROUPID_ARTIFACTID = new HashSet<>();

    private CompositeIndex cached;

    static {
        IGNORED_GROUPID_ARTIFACTID.add("org.graalvm.sdk:graal-sdk");
        IGNORED_GROUPID_ARTIFACTID.add("io.quarkus:quarkus-core");
        IGNORED_GROUPID_ARTIFACTID.add("org.yaml:snakeyaml");
        IGNORED_GROUPID_ARTIFACTID.add("org.wildfly.common:wildfly-common");
        IGNORED_GROUPID_ARTIFACTID.add("org.apache.httpcomponents:httpcore");
        IGNORED_GROUPID_ARTIFACTID.add("com.fasterxml.jackson.core:jackson-core");
        IGNORED_GROUPID_ARTIFACTID.add("org.apache.httpcomponents:httpcore-nio");
        IGNORED_GROUPID_ARTIFACTID.add("io.quarkus:quarkus-vertx-http");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye:smallrye-open-api-core");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.reactive:smallrye-mutiny-vertx-core");
        IGNORED_GROUPID_ARTIFACTID.add("commons-io:commons-io");
        IGNORED_GROUPID_ARTIFACTID.add("org.apache.httpcomponents:httpclient");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.reactive:mutiny");
        IGNORED_GROUPID_ARTIFACTID.add("org.jboss.narayana.jta:narayana-jta");
        IGNORED_GROUPID_ARTIFACTID.add("org.glassfish.jaxb:jaxb-runtime");
        IGNORED_GROUPID_ARTIFACTID.add("com.github.ben-manes.caffeine:caffeine");
        IGNORED_GROUPID_ARTIFACTID.add("com.fasterxml.jackson.core:jackson-databind");
        IGNORED_GROUPID_ARTIFACTID.add("org.hibernate.validator:hibernate-validator");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.config:smallrye-config-core");
        IGNORED_GROUPID_ARTIFACTID.add("com.thoughtworks.xstream:xstream");
        IGNORED_GROUPID_ARTIFACTID.add("com.github.javaparser:javaparser-core");

        IGNORED_GROUPIDS.add("antlr");
        IGNORED_GROUPIDS.add("io.netty");
        IGNORED_GROUPIDS.add("org.drools");
        IGNORED_GROUPIDS.add("net.bytebuddy");
        IGNORED_GROUPIDS.add("io.vertx");
        IGNORED_GROUPIDS.add("org.hibernate");
        IGNORED_GROUPIDS.add("org.kie");
        IGNORED_GROUPIDS.add("org.postgresql");
    }

    @Requirement
    private Logger logger;

    public IndexView createIndex(MavenProject mavenProject, File classesDir, Boolean scanDependenciesDisable,
            List<String> includeDependenciesScopes, List<String> includeDependenciesTypes) throws MojoExecutionException {
        IndexView moduleIndex;
        try {
            moduleIndex = indexModuleClasses(classesDir);
        } catch (IOException e) {
            throw new MojoExecutionException("Can't compute index", e);
        }

        if (scanDependenciesDisable != null && !scanDependenciesDisable) {
            return moduleIndex;
        }
        if (cached != null) {
            return cached;
        }
        List<IndexView> indexes = new ArrayList<>();
        indexes.add(moduleIndex);
        List<Map.Entry<Artifact, Duration>> durations = new ArrayList<>();
        for (Object a : mavenProject.getArtifacts()) {
            Artifact artifact = (Artifact) a;
            if (includeDependenciesScopes.contains(artifact.getScope())
                    && includeDependenciesTypes.contains(artifact.getType())
                    && !IGNORED_GROUPIDS.contains(artifact.getGroupId())
                    && !IGNORED_GROUPID_ARTIFACTID.contains(artifact.getGroupId() + ":" + artifact.getArtifactId())) {

                LocalDateTime start = LocalDateTime.now();

                try {
                    Result result = JarIndexer.createJarIndex(artifact.getFile(), new Indexer(),
                            false, false, false);
                    indexes.add(result.getIndex());
                } catch (Exception e) {
                    logger.error("Can't compute index of " + artifact.getFile().getAbsolutePath() + ", skipping", e);
                }

                LocalDateTime end = LocalDateTime.now();
                durations.add(new AbstractMap.SimpleEntry<>(artifact, Duration.between(start, end)));
            }
        }

        if (logger.isDebugEnabled()) {
            durations.sort(Map.Entry.comparingByValue());

            durations.forEach(e -> {
                logger.debug(e.getKey().getGroupId() + ":" + e.getKey().getArtifactId() + " " + e.getValue());
            });
        }

        CompositeIndex compositeIndex = CompositeIndex.create(indexes);
        cached = compositeIndex;
        return compositeIndex;
    }

    // index the classes of this Maven module
    private Index indexModuleClasses(File classesDir) throws IOException {
        Indexer indexer = new Indexer();

        try (Stream<Path> stream = Files.walk(classesDir.toPath())) {

            List<Path> classFiles = stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
            for (Path path : classFiles) {
                indexer.index(Files.newInputStream(path));
            }
        }
        return indexer.complete();
    }
}
