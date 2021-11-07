package io.smallrye.openapi.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

    @Requirement
    private Logger logger;

    public IndexView createIndex(MavenProject mavenProject, Boolean scanDependenciesDisable,
            List<String> includeDependenciesScopes, List<String> includeDependenciesTypes) throws ExecutionException {

        IndexView moduleIndex = indexCache.get(buildGAVCTString(mavenProject.getArtifact()), () -> {
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
            if (includeDependenciesScopes.contains(artifact.getScope())
                    && includeDependenciesTypes.contains(artifact.getType())) {

                IndexView artifactIndex = indexCache.get(buildGAVCTString(mavenProject.getArtifact()), () -> {
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
        }

        return CompositeIndex.create(indexes);
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
