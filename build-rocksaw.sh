#/bin/sh

if [ ! -e rocksaw-1.1.0-src.tar.gz ];then
  wget https://www.savarese.com/downloads/rocksaw/rocksaw-1.1.0-src.tar.gz
fi
tar -xf rocksaw-1.1.0-src.tar.gz

cd rocksaw-1.1.0

sed -i "s/javac.source=1.3/javac.source=17/g" build.properties
sed -i "s/javac.target=1.3/javac.target=17/g" build.properties

JDK_HOME=$JAVA_HOME ant jar

mkdir -p ../lib
mv lib/librocksaw.so lib/rocksaw-1.1.0.jar ../lib
