/* Copyright 2014 Google Inc. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hudson.plugins.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.SparseCheckoutPath;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.remoting.VirtualChannel;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.util.StreamTaskListener;


/**
 *
 * Since test classes aren't included in maven dependency resolution, these test classes are
 * copied from https://github.com/jenkinsci/git-plugin/
 *
 * Base class for single repository git plugin tests.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase extends HudsonTestCase {
  protected TaskListener listener;

  protected TestGitRepo testRepo;

  // aliases of testRepo properties
  protected PersonIdent johnDoe;
  protected PersonIdent janeDoe;
  protected File workDir; // aliases "gitDir"
  protected FilePath workspace; // aliases "gitDirPath"
  protected GitClient git;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    listener = StreamTaskListener.fromStderr();

    testRepo = new TestGitRepo("unnamed", this, listener);
    johnDoe = testRepo.johnDoe;
    janeDoe = testRepo.janeDoe;
    workDir = testRepo.gitDir;
    workspace = testRepo.gitDirPath;
    git = testRepo.git;
  }

  protected void commit(final String fileName, final PersonIdent committer, final String message)
      throws GitException, InterruptedException {
    testRepo.commit(fileName, committer, message);
  }

  protected void commit(final String fileName, final String fileContent, final PersonIdent committer, final String message)

      throws GitException, InterruptedException {
    testRepo.commit(fileName, fileContent, committer, message);
  }

  protected void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
      final String message) throws GitException, InterruptedException {
    testRepo.commit(fileName, author, committer, message);
  }

  protected List<UserRemoteConfig> createRemoteRepositories() throws IOException {
    return testRepo.remoteConfigs();
  }

  protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter) throws Exception {
    return setupProject(branchString, authorOrCommitter, null);
  }

  protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
      String relativeTargetDir) throws Exception {
    return setupProject(branchString, authorOrCommitter, relativeTargetDir, null, null, null);
  }

  protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
      String relativeTargetDir,
      String excludedRegions,
      String excludedUsers,
      String includedRegions) throws Exception {
    return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, false, includedRegions);
  }

  protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
      String relativeTargetDir,
      String excludedRegions,
      String excludedUsers,
      boolean fastRemotePoll,
      String includedRegions) throws Exception {
    return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, fastRemotePoll, includedRegions);
  }

  protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
      String relativeTargetDir, String excludedRegions,
      String excludedUsers, String localBranch, boolean fastRemotePoll,
      String includedRegions) throws Exception {
    return setupProject(Collections.singletonList(new BranchSpec(branchString)),
        authorOrCommitter, relativeTargetDir, excludedRegions,
        excludedUsers, localBranch, fastRemotePoll,
        includedRegions);
  }

  protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
      String relativeTargetDir, String excludedRegions,
      String excludedUsers, String localBranch, boolean fastRemotePoll,
      String includedRegions) throws Exception {
    return setupProject(branches,
        authorOrCommitter, relativeTargetDir, excludedRegions,
        excludedUsers, localBranch, fastRemotePoll,
        includedRegions, null);
  }

  protected FreeStyleProject setupProject(String branchString, List<SparseCheckoutPath> sparseCheckoutPaths) throws Exception {
    return setupProject(Collections.singletonList(new BranchSpec(branchString)),
        false, null, null,
        null, null, false,
        null, sparseCheckoutPaths);
  }

  protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
      String relativeTargetDir, String excludedRegions,
      String excludedUsers, String localBranch, boolean fastRemotePoll,
      String includedRegions, List<SparseCheckoutPath> sparseCheckoutPaths) throws Exception {
    FreeStyleProject project = createFreeStyleProject();
    GitSCM scm = new GitSCM(
        createRemoteRepositories(),
        branches,
        false, Collections.<SubmoduleConfig>emptyList(),
        null, null,
        Collections.<GitSCMExtension>emptyList());
    scm.getExtensions().add(new DisableRemotePoll()); // don't work on a file:// repository
    if (relativeTargetDir!=null)
      scm.getExtensions().add(new RelativeTargetDirectory(relativeTargetDir));
    if (excludedUsers!=null)
      scm.getExtensions().add(new UserExclusion(excludedUsers));
    if (excludedRegions!=null || includedRegions!=null)
      scm.getExtensions().add(new PathRestriction(includedRegions,excludedRegions));

    scm.getExtensions().add(new SparseCheckoutPaths(sparseCheckoutPaths));

    project.setScm(scm);
    project.getBuildersList().add(new CaptureEnvironmentBuilder());
    return project;
  }

  protected FreeStyleProject setupSimpleProject(String branchString) throws Exception {
    return setupProject(branchString,false);
  }

  protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
    final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
    System.out.println(build.getLog());
    for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
      assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
    }
    if(expectedResult != null) {
      assertBuildStatus(expectedResult, build);
    }
    return build;
  }

  protected FreeStyleBuild build(final FreeStyleProject project, final String parentDir, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
    final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
    System.out.println(build.getLog());
    for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
      assertTrue(build.getWorkspace().child(parentDir).child(expectedNewlyCommittedFile).exists());
    }
    if(expectedResult != null) {
      assertBuildStatus(expectedResult, build);
    }
    return build;
  }

  protected EnvVars getEnvVars(FreeStyleProject project) {
    for (hudson.tasks.Builder b : project.getBuilders()) {
      if (b instanceof CaptureEnvironmentBuilder) {
        return ((CaptureEnvironmentBuilder)b).getEnvVars();
      }
    }
    return new EnvVars();
  }

  protected void setVariables(Node node, EnvironmentVariablesNodeProperty.Entry... entries) throws IOException {
    node.getNodeProperties().replaceBy(
        Collections.singleton(new EnvironmentVariablesNodeProperty(
            entries)));

  }

  protected String getHeadRevision(AbstractBuild build, final String branch) throws IOException, InterruptedException {
    return build.getWorkspace().act(new FilePath.FileCallable<String>() {
      public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        try {
          ObjectId oid = Git.with(null, null).in(f).getClient().getRepository().resolve("refs/heads/" + branch);
          return oid.name();
        } catch (GitException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
