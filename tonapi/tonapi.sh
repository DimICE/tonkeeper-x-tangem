curl -o openapi.yml 'https://raw.githubusercontent.com/tonkeeper/opentonapi/master/api/openapi.yml'

openapi-generator generate -i openapi.yml -g kotlin --additional-properties packageName=io.tonapi


rm -rf openapi.yml
rm -rf settings.gradle
rm -rf docs
rm -rf gradle
rm -rf .openapi-generator
rm -rf README.md
rm -rf build.gradle
rm -rf .openapi-generator-ignore
rm -rf gradlew
rm -rf gradlew.bat
rm -rf src/test
