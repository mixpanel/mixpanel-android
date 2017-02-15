#!/bin/bash

# externalize vars
if [ ! -f gradle.properties ]; then
    echo "gradle.properties was not found. Make sure you are running this script from its root folder." 
    exit
fi

currentBranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')
releaseBranch=master
docBranch=gh-pages

# stash any changes
git stash

# fetch relese branch
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

if [ ! -f README.md.bak ]; then
    echo "Err... README.md was not updated. The following command was used:"
    echo "sed -i.bak 's,^\(##### _\).*\(_ - \[v\).*\(](https://github.com/mixpanel/mixpanel-android/releases/tag/v\).*\()\),\1$newDate\2$releaseVersion\3$releaseVersion\4,' README.md"
    cp gradle.properties.bak gradle.properties
    cp README.md.bak README.md
    rm gradle.properties.bak
    rm README.md.bak    
    exit
fi

if [ ! -f gradle.properties.bak ]; then
    echo "Err... gradle.properties was not updated. The following command was used:"
    echo "sed -i.bak 's,^\(VERSION_NAME=\).*,\1'$releaseVersion',' gradle.properties"
    cp gradle.properties.bak gradle.properties
    cp README.md.bak README.md
    rm gradle.properties.bak
    rm README.md.bak    
    exit
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
    cp gradle.properties.bak gradle.properties
    cp README.md.bak README.md
    rm gradle.properties.bak
    rm README.md.bak
    exit
fi

printf "\n\n"

# remove backup file
rm gradle.properties.bak
rm README.md.bak

# commit new version
git commit -am "New release: $releaseVersion"

# push changes
git push origin $releaseBranch

# create new tag
newTag=v$releaseVersion
git tag $newTag
git push origin $newTag

# upload library to maven
./gradlew uploadArchives

# update next snapshot version
read -r -p "Continue updating github with the next snasphot version $nextSnapshotVersion? [y/n]: " key
if ! [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    sed -i.bak 's,^\(VERSION_NAME=\).*,\1'$nextSnapshotVersion',' gradle.properties
    printf "New gradle.properties:\n"
    printf '%s\n' '-----------------------'
    head -n 1 gradle.properties
    printf '[....]\n\n\n'

    read -r -p "Does this look right to you? [y/n]: " key

    if [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
        git commit -am "Update master with next snasphot version $nextSnapshotVersion"
        git push origin master
    else
        printf "\nReverting....\n"
        cp gradle.properties.bak gradle.properties
        rm gradle.properties.bak
    fi
fi

# update documentation
read -r -p "Do you want to update the documentation? [y/n]: " key
if ! [[ "$key" =~ ^([yY][eE][sS]|[yY])+$ ]]; then
    printf "\nDone!\n"
    exit
fi
git checkout $docBranch
git pull origin origin $docBranch
cp -r build/docs/javadoc/* .
git commit -am "Update documentation for $releaseVersion"
git push origin gh-pages


# restore previous state
git checkout $currentBranch
git stash pop
