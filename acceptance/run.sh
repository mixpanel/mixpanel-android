#!/bin/bash

TEST_APP_DIR=test-application
TESTS=(test_property.py test_layout.py)
HTTP_SERVER_PID=NULL
SELEDROID_PID=NULL
ANDROID_SDK_DIR=~/Library/Android/sdk/

function build_apk {
    pushd ${TEST_APP_DIR}
    export ANDROID_HOME=$ANDROID_SDK_DIR
    ../../gradlew clean :assembleDebug
    popd
}

function start_node_server {
    python http_server.py&
    HTTP_SERVER_PID=$!
}

function start_selendroid_server {
    export ANDROID_HOME=$ANDROID_SDK_DIR
    java -jar selendroid-standalone-0.15.0-with-dependencies.jar -app $TEST_APP_DIR/build/outputs/apk/test-application-debug.apk&
    SELEDROID_PID=$!
}

function run_test {
    total_cnt=0
    failure_cnt=0
    for test_file in ${TESTS[@]}
    do
        python $test_file
        if [ $? != 0 ]
        then
            let failure_cnt+=1
        fi
        let total_cnt+=1
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
    if [ $HTTP_SERVER_PID != NULL ]
    then
        kill $HTTP_SERVER_PID
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
