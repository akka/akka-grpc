Release Akka gRPC $VERSION$

<!--

(Liberally copied and adopted from Scala itself https://github.com/scala/scala-dev/blob/b11cd2e4a4431de7867db6b39362bea8fa6650e7/notes/releases/template.md)

For every release, make a copy of this file named after the release, and expand the variables.
Ideally replacing variables could become a script you can run on your local machine.

Variables to be expanded in this template:
- $VERSION$=??? 

-->

### Before the release

- [ ] Make sure all important / big PRs have been merged by now
- [ ] Create a news item draft PR on [akka.github.com](https://github.com/akka/akka.github.com), using the [draft release](https://github.com/akka/akka-grpc/releases)

### Cutting the release

- [ ] Make sure the [Github Actions build](https://github.com/akka/akka-grpc/actions?query=branch%3Amain) for the commit you would like to release has completed.
- [ ] Tag the release `git tag -a -m 'Release v$VERSION$' v$VERSION$` and push the tag `git push --tags`
- [ ] Check that the GitHub Actions release build has executed successfully (it should publish artifacts to Sonatype and documentation to Gustav)

### Check availability

- [ ] Check [reference](https://doc.akka.io/docs/akka-grpc/$VERSION$/) documentation
- [ ] Check the release on [Maven central](https://repo1.maven.org/maven2/com/lightbend/akka/grpc/akka-grpc-scalapb-protoc-plugin_2.12/$VERSION$/)

### When everything is on maven central
  - [ ] `ssh akkarepo@gustav.akka.io`
    - [ ] update the `current` links on `repo.akka.io` to point to the latest version with
         ```
         ln -nsf $VERSION$ www/docs/akka-grpc/current
         ln -nsf $VERSION$ www/api/akka-grpc/current
         ```
    - [ ] check changes and commit the new version to the local git repository
         ```
         cd ~/www
         git add docs/akka-grpc/current docs/akka-grpc/$VERSION$
         git add api/akka-grpc/current api/akka-grpc/$VERSION$
         git commit -m "Akka gRPC $VERSION$"
         ```
    - [ ] push changes to the [remote git repository](https://github.com/akka/doc.akka.io)
         ```
         cd ~/www
         git push origin master
         ```

### Announcements

- [ ] Merge draft news item for [akka.io](https://github.com/akka/akka.github.com)
- [ ] Edit the [release draft](https://github.com/akka/akka-grpc/releases) with the next tag version `v$VERSION$`, title and release description.
- [ ] Send a release notification to [Lightbend discuss](https://discuss.akka.io)
- [ ] Tweet using the akkateam account (or ask someone to) about the new release
- [ ] Announce on [Gitter akka/akka](https://gitter.im/akka/akka)
- [ ] Announce internally

### Afterwards

- Close this issue
