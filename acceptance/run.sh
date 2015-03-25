#!/bin/bash

CONFIG_FILE=config
SAMPLE_APP_DIR=sample-android-mixpanel-integration
TESTS=(test.py)
NODE_SERVER_PID=NULL
SELEDROID_PID=NULL

function build_apk {
	pushd ${SAMPLE_APP_DIR} > /dev/null
	./gradlew clean :assembleDebug
	popd > /dev/null
}

function start_node_server {
    python http_server.py&
	NODE_SERVER_PID=$!
}

function start_selendroid_server {
	export ANDROID_HOME=~/Library/Android/sdk/
	java -jar selendroid-standalone-0.15.0-with-dependencies.jar -app sample-android-mixpanel-integration/build/outputs/apk/sample-android-mixpanel-integration-debug.apk&
	SELEDROID_PID=$!
}

function run_test {
	total_cnt=0
	failure_cnt=0
	for test_file in $TESTS
	do
		python $test_file
		if [ $? != 0 ]
		then
			let failure_cnt+=1
		fi
		let total_cnt=total_cnt+1
	done

	bold=`tput bold`
	green=`tput setaf 2`
	red=`tput setaf 1`
	normal=`tput sgr0`
	echo ""
	echo "${bold}Total test sutie(s): ${total_cnt}${normal}"
	echo "${green}Passed test suite(s): $((total_cnt-failure_cnt))${normal}"
	echo "${red}Failed test suite(s): ${failure_cnt}${normal}"
}

function clean_up {
	if [ $NODE_SERVER_PID != NULL ]
	then
		kill $NODE_SERVER_PID
	fi
	
	if [ $SELEDROID_PID != NULL ]
	then
		kill $SELEDROID_PID
	fi
}

build_apk
start_node_server
start_selendroid_server
# wait for server initialization
sleep 10
run_test
clean_up
