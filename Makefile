######################
#      Makefile      #
######################

DP3T_SDK = dpppt-backend-sdk
DP3T_SDK_WS = $(DP3T_SDK)/dpppt-backend-sdk-ws
DP3T_SDK_INTEROPS = $(DP3T_SDK)/dpppt-backend-sdk-interops-efgs

FILE_NAME = documentation.tex

LATEX = xelatex
BIBER = biber
RUSTY_SWAGGER = rusty-swagger
DOCKER_REPO = peppptdweacr.azurecr.io
IMAGE_TAG = latest
TARGET_ENV = dev

all: clean all1
#all1: clean updateproject updatedoc swagger la la2 la3 
all1: clean updateproject
no: clean updateproject updatedoc swagger la la2 
docker-build: updateproject docker-ws docker-interops
doc: updatedoc swagger la la2 la3
test: clean run-test
run-test:
	mvn -f $(DP3T_SDK)/pom.xml test

package:
	mvn -f $(DP3T_SDK)/pom.xml clean package

updateproject:
	mvn -f $(DP3T_SDK)/pom.xml package -DskipTests

updatedoc:
	mvn -f $(DP3T_SDK)/pom.xml package -Dmaven.test.skip=true
	mvn springboot-swagger-3:springboot-swagger-3 -f $(DP3T_SDK_WS)/pom.xml
	cp $(DP3T_SDK_WS)/generated/swagger/swagger.yaml documentation/yaml/sdk.yaml

swagger:
	cd documentation; $(RUSTY_SWAGGER) --file ../$(DP3T_SDK_WS)/generated/swagger/swagger.yaml

la:
	cd documentation;$(LATEX) $(FILE_NAME)
bib:
	cd documentation;$(BIBER) $(FILE_NAME)
la2:
	cd documentation;$(LATEX) $(FILE_NAME)
la3:
	cd documentation;$(LATEX) $(FILE_NAME)
show:
	cd documentation; open $(FILE_NAME).pdf &

docker-ws:
	cp $(DP3T_SDK_WS)/target/dpppt-backend-sdk-ws.jar ws-sdk/ws/bin/dpppt-backend-sdk-ws.jar
	docker build --build-arg targetenv=${TARGET_ENV} -t ${DOCKER_REPO}/dpppt-mt-ws:${IMAGE_TAG} ws-sdk/
	@printf '\033[33m DO NOT USE THIS IN PRODUCTION \033[0m \n'
	@printf "\033[32m docker run -p 8080:8080 -v $(PWD)/$(DP3T_SDK_WS)/src/main/resources/logback.xml:/home/ws/conf/dpppt-backend-sdk-ws-logback.xml -v $(PWD)/$(DP3T_SDK_WS)/src/main/resources/application.properties:/home/ws/conf/dpppt-backend-sdk-ws.properties dp3t-docker \033[0m\n"

docker-interops:
	cp $(DP3T_SDK_INTEROPS)/target/dpppt-backend-sdk-interops-efgs.jar ws-sdk-interops/ws/bin/dpppt-backend-sdk-interops-efgs.jar
	docker build --build-arg targetenv=${TARGET_ENV} -t ${DOCKER_REPO}/dpppt-mt-ws-interops:${IMAGE_TAG} ws-sdk-interops/
	@printf '\033[33m DO NOT USE THIS IN PRODUCTION \033[0m \n'
	@printf "\033[32m docker run -p 8080:8080 -v $(PWD)/$(DP3T_SDK_INTEROPS)/src/main/resources/logback.xml:/home/ws/conf/dpppt-backend-sdk-ws-logback.xml -v $(PWD)/$(DP3T_SDK_INTEROPS)/src/main/resources/application.properties:/home/ws/conf/dpppt-backend-sdk-ws.properties dp3t-docker \033[0m\n"

clean:
	mvn -f $(DP3T_SDK)/pom.xml clean
	@rm -f $(DP3T_SDK_WS)/dp3t-ws.log*
	@rm -f documentation/*.log documentation/*.aux documentation/*.dvi documentation/*.ps documentation/*.blg documentation/*.bbl documentation/*.out documentation/*.bcf documentation/*.run.xml documentation/*.fdb_latexmk documentation/*.fls documentation/*.toc
