#!/bin/bash
# This script automates all the tasks needed to make a new Mixpanel Android SDK release.
#
# Usage: ./release.sh [X.X.X] where X.X.X is the release version. This param is optional.
#
# If no version is given the next release version used will be the one that appears
# on gradle.properties (VERSION_NAME).

if [ ! -f gradle.properties ]; then
    echo "gradle.properties was not found. Make sure you are running this script from its root folder." 
    exit
fi
if [ ! -f ~/.gradle/gradle.properties.bak ]; then
    echo "~/.gradle/gradle.properties.bak was not found" 
    exit
fi

abort () {
    restoreFiles
    cleanUp
    quit
}

quit () {
    mv ~/.gradle/gradle.properties ~/.gradle/gradle.properties.bak
    git checkout $currentBranch
    exit
}

cleanUp () {
    if [ -f gradle.properties.bak ]; then
        rm gradle.properties.bak   
    fi
    if [ -f README.md.bak ]; then
        rm README.md.bak  
    fi
}

restoreFiles () {
    git checkout -- gradle.properties
    git checkout -- README.md
}

mv ~/.gradle/gradle.properties.bak ~/.gradle/gradle.properties

currentBranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')
releaseBranch=master
docBranch=gh-pages

# checkout release branch
git checkout $releaseBranch
git pull origin $releaseBranch

# find release version: if no args we grab gradle.properties without -SNAPSHOT
if [ -z "$1" ]
  then
    releaseVersion=$(head -n 1 gradle.properties | sed -e 's/VERSION_NAME=\(.*\)-SNAPSHOT/\1/')
else
    releaseVersion=$1
fi

# find next snapshot version by incrementing the release version
nextSnapshotVersion=$(echo $releaseVersion | awk -F. -v OFS=. 'NF==1{print ++$NF}; NF>1{if(length($NF+1)>length($NF))$(NF-1)++; $NF=sprintf("%0*d", length($NF), ($NF+1)%(10^length($NF))); print}')-SNAPSHOT

# change version on gradle.properties - Make sure there are no spaces. Expected format: VERSION_NAME=.*
sed -i.bak 's,^\(VERSION_NAME=\).*,\1'$releaseVersion',' gradle.properties

# change date latest release
newDate=$(date "+%B %d\, %Y") # Need the slash before the comma so next command does not fail
sed -i.bak "s,^\(##### _\).*\(_ - \[v\).*\(](https://github.com/mixpanel/mixpanel-android/releases/tag/v\).*\()\),\1$newDate\2$releaseVersion\3$releaseVersion\4," README.md

printf '\n\n'
if [ ! -f README.md.bak ]; then
    echo "Err... README.md was not updated. The following command was used:"
    echo "sed -i.bak 's,^\(##### _\).*\(_ - \[v\).*\(](https://github.com/mixpanel/mixpanel-android/releases/tag/v\).*\()\),\1$newDate\2$releaseVersion\3$releaseVersion\4,' README.md"
    abort
fi

if [ ! -f gradle.properties.bak ]; then
    echo "Err... gradle.properties was not updated. The following command was used:"
    echo "sed -i.bak 's,^\(VERSION_NAME=\).*,\1'$releaseVersion',' gradle.properties"
    abort
fi

printf "New gradle.properties:\n"
printf '%s\n' '-----------------------'
head -n 1 gradle.properties
printf '[....]\n\n\n'

printf "New README.md:\n"
printf '%s\n' '-----------------------'
head -n 9 README.md
printf '[....]\n\n\n'

read -r -p "Does this look right to you? [y/n]: " key

if ! [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    printf "\nBummer! Aborting release...\n"
    abort
fi

# remove backup file
cleanUp

# upload library to maven
printf '\n\nUploading archives....\n'
if ! ./gradlew uploadArchives ; then
    printf "Err.. Seems there was a problem runing ./gradlew uploadArchives"
    abort
fi

read -r -p "Continue pushing to github? [y/n]: " key
if ! [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    abort
fi

# commit new version
git commit -am "New release: $releaseVersion"

# push changes
git push origin $releaseBranch

# create new tag
newTag=v$releaseVersion
git tag $newTag
git push origin $newTag

# update next snapshot version
printf '\nUpdating next snapshot version...\n'
sed -i.bak 's,^\(VERSION_NAME=\).*,\1'$nextSnapshotVersion',' gradle.properties
printf "\nNew gradle.properties:\n"
printf '%s\n' '-----------------------'
head -n 1 gradle.properties
printf '[....]\n\n\n'

read -r -p "Does this look right to you? [y/n]: " key
if [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    printf '\n\n'
    git commit -am "Update master with next snasphot version $nextSnapshotVersion"
    git push origin master
else
    printf "\nReverting.... Make sure to update this manually.\n"
    restoreFiles
fi

cleanUp

# update documentation
printf '\n\nUpdating documentation...\n\n'
git checkout $docBranch
git pull origin $docBranch
cp -r build/docs/javadoc/* .
git commit -am "Update documentation for $releaseVersion"
git push origin gh-pages

printf '\nAll done!\n'
printf 'Make sure you make a new release at https://github.com/mixpanel/mixpanel-android/releases/new\n'
printf 'Also, do not forget to update our CHANGELOG (https://github.com/mixpanel/mixpanel-android/wiki/Changelog)\n'
printf 'And finally, release the library from https://oss.sonatype.org/index.html\n\n'

quit
