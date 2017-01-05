# checktool
Tool to verify that the AMS file objects have been imported into the database

[![Build Status](https://travis-ci.org/darioajr/checktool.svg?branch=master)](https://travis-ci.org/darioajr/checktool)
[![Code Coverage](https://codecov.io/github/darioajr/checktool/coverage.svg)](https://codecov.io/gh/darioajr/checktool)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/7329432a75fe42daa1d6415596f7b5bd)](https://www.codacy.com/app/darioajr/checktool?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=darioajr/checktool&amp;utm_campaign=Badge_Grade)



| Parameters                                                        |
| ----------------------------------------------------------------  |
| -a,--ams <arg>          input ams file path                       |
|-c,--connection <arg>   database connection -
                         jdbc:oracle:thin:@server:port/service_name |
| -f,--force              force version by objUUID  (Optional)      |
| -p,--password <arg>     database password                         |
| -u,--user <arg>         database user                             |

 Usage
 -----
 ```
 java -jar checktool.jar -a file.ams -c jdbc:oracle:thin:@xx.xx.xx.xx:Port/ServiceName -u userName -p Password
 ```
 
 Build
 -----
 ```
 mvn package
 ```
 
 Generate Eclipse Project
 -----
 ```
 mvn eclipse:eclipse
 ```
 
