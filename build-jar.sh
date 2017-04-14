outpath="out/production/vmapp"
clientpath="com/alexgaiv/vmclient"
serverpath="com/alexgaiv/vmserver"

jar cfm vmclient.jar client-manifest.mf -C $outpath $clientpath

mkdir temp
cd temp
jar -xf ../lib/sqlite-jdbc-3.16.1.jar
cd ..
cp -r $outpath/* temp
jar cfm vmserver.jar server-manifest.mf -C temp $serverpath temp/org
rm -r temp