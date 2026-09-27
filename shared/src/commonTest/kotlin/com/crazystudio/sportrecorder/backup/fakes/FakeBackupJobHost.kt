package com.crazystudio.sportrecorder.backup.fakes

import com.crazystudio.sportrecorder.backup.BackupJobHost

class FakeBackupJobHost : BackupJobHost {
    var started = 0
        private set
    var finished = 0
        private set
    override fun onJobStarted() { started++ }
    override fun onJobFinished() { finished++ }
}
