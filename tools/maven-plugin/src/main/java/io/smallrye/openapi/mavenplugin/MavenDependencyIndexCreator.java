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
import java.util.concurrent.Callable;
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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

@Component(role = MavenDependencyIndexCreator.class, instantiationStrategy = "singleton")
public class MavenDependencyIndexCreator {

    private final Cache<String, IndexView> indexCache = CacheBuilder.newBuilder().build();

    private static final Set<String> IGNORED_GROUPIDS = new HashSet<>();
    private static final Set<String> IGNORED_GROUPID_ARTIFACTID = new HashSet<>();

    static {
        IGNORED_GROUPID_ARTIFACTID.add("org.graalvm.sdk:graal-sdk");
        IGNORED_GROUPID_ARTIFACTID.add("org.yaml:snakeyaml");
        IGNORED_GROUPID_ARTIFACTID.add("org.wildfly.common:wildfly-common");
        IGNORED_GROUPID_ARTIFACTID.add("com.fasterxml.jackson.core:jackson-core");
        IGNORED_GROUPID_ARTIFACTID.add("com.fasterxml.jackson.core:jackson-databind");
        IGNORED_GROUPID_ARTIFACTID.add("io.quarkus:quarkus-vertx-http");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye:smallrye-open-api-core");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.reactive:smallrye-mutiny-vertx-core");
        IGNORED_GROUPID_ARTIFACTID.add("commons-io:commons-io");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.reactive:mutiny");
        IGNORED_GROUPID_ARTIFACTID.add("org.jboss.narayana.jta:narayana-jta");
        IGNORED_GROUPID_ARTIFACTID.add("org.glassfish.jaxb:jaxb-runtime");
        IGNORED_GROUPID_ARTIFACTID.add("com.github.ben-manes.caffeine:caffeine");
        IGNORED_GROUPID_ARTIFACTID.add("org.hibernate.validator:hibernate-validator");
        IGNORED_GROUPID_ARTIFACTID.add("io.smallrye.config:smallrye-config-core");
        IGNORED_GROUPID_ARTIFACTID.add("com.thoughtworks.xstream:xstream");
        IGNORED_GROUPID_ARTIFACTID.add("com.github.javaparser:javaparser-core");
        IGNORED_GROUPID_ARTIFACTID.add("org.jboss:jandex");

        IGNORED_GROUPIDS.add("antlr");
        IGNORED_GROUPIDS.add("io.netty");
        IGNORED_GROUPIDS.add("org.drools");
        IGNORED_GROUPIDS.add("net.bytebuddy");
        IGNORED_GROUPIDS.add("io.vertx");
        IGNORED_GROUPIDS.add("org.hibernate");
        IGNORED_GROUPIDS.add("org.kie");
        IGNORED_GROUPIDS.add("org.postgresql");
        IGNORED_GROUPIDS.add("org.apache.httpcomponents");
    }

    @Requirement
    private Logger logger;

    public IndexView createIndex(MavenProject mavenProject, Boolean scanDependenciesDisable,
            List<String> includeDependenciesScopes, List<String> includeDependenciesTypes) throws Exception {

        List<Map.Entry<Artifact, Duration>> indexDurations = new ArrayList<>();

        IndexView moduleIndex = timeAndCache(indexDurations, mavenProject.getArtifact(), () -> {
            try {
                return indexModuleClasses(mavenProject);
            } catch (IOException e) {
                throw new MojoExecutionException("Can't compute index", e);
            }
        });

        if (scanDependenciesDisable != null && !scanDependenciesDisable) {
            return moduleIndex;
        }

        List<IndexView> indexes = new ArrayList<>();
        indexes.add(moduleIndex);
        for (Artifact artifact : mavenProject.getArtifacts()) {
            if (isIgnored(artifact, includeDependenciesScopes, includeDependenciesTypes)) {
                continue;
            }

            IndexView artifactIndex = timeAndCache(indexDurations, artifact, () -> {
                try {
                    Result result = JarIndexer.createJarIndex(artifact.getFile(), new Indexer(),
                            false, false, false);
                    return result.getIndex();
                } catch (Exception e) {
                    logger.error("Can't compute index of " + artifact.getFile().getAbsolutePath() + ", skipping", e);
                    return null;
                }
            });

            if (artifactIndex != null) {
                indexes.add(artifactIndex);
            }
        }

        printIndexDurations(indexDurations);

        return CompositeIndex.create(indexes);
    }

    private void printIndexDurations(List<Map.Entry<Artifact, Duration>> indexDurations) {
        if (logger.isDebugEnabled()) {
            indexDurations.sort(Map.Entry.comparingByValue());

            indexDurations.forEach(e -> {
                if (!e.getValue().isZero()) {
                    logger.debug(buildGAVCTString(e.getKey()) + " " + e.getValue());
                }
            });
        }
    }

    private boolean isIgnored(Artifact artifact, List<String> includeDependenciesScopes,
            List<String> includeDependenciesTypes) {
        return !includeDependenciesScopes.contains(artifact.getScope())
                || !includeDependenciesTypes.contains(artifact.getType())
                || IGNORED_GROUPIDS.contains(artifact.getGroupId())
                || IGNORED_GROUPID_ARTIFACTID.contains(artifact.getGroupId() + ":" + artifact.getArtifactId());
    }

    private IndexView timeAndCache(List<Map.Entry<Artifact, Duration>> durations, Artifact artifact,
            Callable<IndexView> callable) throws Exception {
        LocalDateTime start = LocalDateTime.now();
        IndexView result = indexCache.get(buildGAVCTString(artifact), callable);
        LocalDateTime end = LocalDateTime.now();

        Duration duration = Duration.between(start, end);
        durations.add(new AbstractMap.SimpleEntry<>(artifact, duration));

        return result;
    }

    // index the classes of this Maven module
    private Index indexModuleClasses(MavenProject mavenProject) throws IOException {
        Indexer indexer = new Indexer();

        try (Stream<Path> stream = Files.walk(new File(mavenProject.getBuild().getOutputDirectory()).toPath())) {

            List<Path> classFiles = stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
            for (Path path : classFiles) {
                indexer.index(Files.newInputStream(path));
            }
        }
        return indexer.complete();
    }

    private String buildGAVCTString(Artifact artifact) {
        return artifact.getGroupId() +
                ":" +
                artifact.getArtifactId() +
                ":" +
                artifact.getVersion() +
                ":" +
                artifact.getClassifier() +
                ":" +
                artifact.getType();
    }
}
