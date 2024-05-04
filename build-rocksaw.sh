#/bin/sh

if [ ! -e rocksaw-1.1.0-src.tar.gz ];then
  wget https://www.savarese.com/downloads/rocksaw/rocksaw-1.1.0-src.tar.gz
fi
tar -xf rocksaw-1.1.0-src.tar.gz

patch -p1 < ./rocksaw-diff.patch

cd rocksaw-1.1.0

JDK_HOME=$JAVA_HOME ant jar

mkdir -p ../lib
mv lib/* ../lib
