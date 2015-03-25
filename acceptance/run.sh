#!/bin/bash

CONFIG_FILE=config
SAMPLE_APP_DIR=sample-android-mixpanel-integration
TESTS=(test.py)
NODE_SERVER_PID=NULL
SELEDROID_PID=NULL
NPM_PACKAGES_REQUIRED=(express)

VERBOSE=0

function npm_package_check {
	for package in $NPM_PACKAGES_REQUIRED
	do
		ls node_modules 2> /dev/null | grep $package > /dev/null
		if [ $? != 0 ]
		then
			echo "$package is missing. installing.."
			if [ $VERBOSE != 0 ]
			then
				sudo npm install $package --save
			else
				sudo npm install $package --save > /dev/null
			fi
		fi
	done
}

function parse_config {
	while IFS="=" read -r key value
	do
		case $key in
			'MIXPANEL_API_TOKEN')
				sed -i -e "s/MIXPANEL_API_TOKEN = \"YOUR API TOKEN\"/MIXPANEL_API_TOKEN = \"${value}\"/g" $SAMPLE_APP_DIR/src/com/mixpanel/example/hello/MainActivity.java
			;;
		esac
	done < $CONFIG_FILE
}

function build_apk {
	pushd ${SAMPLE_APP_DIR} > /dev/null
	if [ $VERBOSE != 0 ]
	then
		./gradlew clean :assembleDebug
	else
		./gradlew clean :assembleDebug > /dev/null
	fi
	popd > /dev/null
}

function start_node_server {
	if [ $VERBOSE != 0 ]
	then
		node server.js&
	else
		node server.js > /dev/null&
	fi
	NODE_SERVER_PID=$!
}

function start_selendroid_server {
	export ANDROID_HOME=~/Library/Android/sdk/
	if [ $VERBOSE != 0 ]
	then
		java -jar selendroid-standalone-0.15.0-with-dependencies.jar -app sample-android-mixpanel-integration/build/outputs/apk/sample-android-mixpanel-integration-debug.apk&
	else
		java -jar selendroid-standalone-0.15.0-with-dependencies.jar -app sample-android-mixpanel-integration/build/outputs/apk/sample-android-mixpanel-integration-debug.apk > /dev/null 2> /dev/null&
	fi
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

	echo "Total test suite(s): ${total_cnt}"
	echo "Failed test suite(s): ${failure_cnt}"
}

function clean_up {
	if [ $NODE_SERVER_PID != NULL ]
	then
		kill -9 $NODE_SERVER_PID
	fi
	
	if [ $SELEDROID_PID != NULL ]
	then
		kill -9 $SELEDROID_PID
	fi
}

echo "looking for required module - express.."
npm_package_check

echo "updating the sample app according to the config file.."
parse_config

echo "rebuilding the sample app.."
build_apk

echo "starting the local decide server.."
start_node_server

echo "starting the seledroid server.."
start_selendroid_server

echo "waiting for server initialization.."
sleep 10

echo "running test.."
run_test

echo "cleaning up the background processes.."
clean_up
