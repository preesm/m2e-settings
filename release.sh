#!/bin/bash -eu

### Config
DEV_BRANCH=develop
MAIN_BRANCH=master

CURRENT_VERSION=`mvn -B -Dtycho.mode=maven help:evaluate -Dexpression=project.version | grep -v 'INFO'`
[ "$#" -ne "1" ] && echo -e "usage: $0 <new version>\nNote: current version = ${CURRENT_VERSION}" && exit 1

# First check access on git (will exit on error)
echo "Testing Github permission"
git ls-remote git@github.com:preesm/m2e-settings.git > /dev/null

#warning
echo "Warning: this script will delete ignored files and remove all changes in $DEV_BRANCH and $MAIN_BRANCH"
read -p "Do you want to conitnue ? [NO/yes] " ANS
LCANS=`echo "${ANS}" | tr '[:upper:]' '[:lower:]'`
[ "${LCANS}" != "yes" ] && echo "Aborting." && exit 1

NEW_VERSION=$1

CURRENT_BRANCH=$(cd `dirname $0` && echo `git branch`)
ORIG_DIR=`pwd`
DIR=$(cd `dirname $0` && echo `git rev-parse --show-toplevel`)
TODAY_DATE=`date +%Y.%m.%d`
#change to git root dir
cd $DIR

#move to dev branch and clean repo
git checkout $DEV_BRANCH
git reset --hard
git clean -xdf

#update version in code and stash changes
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$NEW_VERSION -Dtycho.mode=maven

#commit new version in develop
git commit -am "[RELENG] Prepare version $NEW_VERSION"

#merge in master, add tag
git checkout $MAIN_BRANCH
git merge --no-ff $DEV_BRANCH -m "merge branch '$DEV_BRANCH' for new version $NEW_VERSION"
git tag v$NEW_VERSION

#move to snapshot version in develop and push
git checkout $DEV_BRANCH
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$NEW_VERSION-SNAPSHOT -Dtycho.mode=maven
git commit -am "[RELENG] Move to snapshot version"

#deploy from master
git checkout $MAIN_BRANCH

mvn -e -C -U -V clean verify

git checkout gh-pages
rm site/ -rf
cp nl.topicus.m2e.settings.repository/target/repository/ site -R

git add site
git commit -m "[RELENG] Update site to version $NEW_VERSION"

git clean -xdf
