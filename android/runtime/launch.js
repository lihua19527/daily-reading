'use strict';

const fs = require('node:fs');

const [, , dataDir, readyFile, session, port, timezone] = process.argv;
process.env.BCZ_READER_DATA = dataDir;
process.env.DR_ANDROID_SESSION = session;
process.env.TZ = timezone;

function report(status) {
  const tempFile = readyFile + '.tmp';
  fs.writeFileSync(tempFile, status);
  fs.renameSync(tempFile, readyFile);
}

require('./server').start(Number(port)).then(
  () => report('ready'),
  (error) => report(error.message)
);
