package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.observe.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.SnapshotStorageType

/**
 * Snapshot 设置 Repository
 *
 * 负责：
 * - Snapshot 开关
 * - Storage 类型
 * - Auto Checkpoint 开关
 * - Rollback Strategy
 *
 * 对应页面：
 * - SnapshotConfigActivity
 * - CheckpointRecovery.kt
 * - ChatViewModel 中 snapshot 相关条件判断
 */
interface SnapshotSettingsRepository {
    
    fun isSnapshotEnabled(): Boolean
    fun setSnapshotEnabled(enabled: Boolean)
    
    fun getSnapshotStorage(): SnapshotStorageType
    fun setSnapshotStorage(type: SnapshotStorageType)
    
    fun isSnapshotAutoCheckpoint(): Boolean
    fun setSnapshotAutoCheckpoint(enabled: Boolean)
    
    fun getSnapshotRollbackStrategy(): RollbackStrategy
    fun setSnapshotRollbackStrategy(strategy: RollbackStrategy)
}

class DefaultSnapshotSettingsRepository(
    private val configRepo: ConfigRepository
) : SnapshotSettingsRepository {
    
    override fun isSnapshotEnabled(): Boolean = configRepo.isSnapshotEnabled()
    
    override fun setSnapshotEnabled(enabled: Boolean) {
        configRepo.setSnapshotEnabled(enabled)
    }
    
    override fun getSnapshotStorage(): SnapshotStorageType = configRepo.getSnapshotStorage()
    
    override fun setSnapshotStorage(type: SnapshotStorageType) {
        configRepo.setSnapshotStorage(type)
    }
    
    override fun isSnapshotAutoCheckpoint(): Boolean = configRepo.isSnapshotAutoCheckpoint()
    
    override fun setSnapshotAutoCheckpoint(enabled: Boolean) {
        configRepo.setSnapshotAutoCheckpoint(enabled)
    }
    
    override fun getSnapshotRollbackStrategy(): RollbackStrategy = 
        configRepo.getSnapshotRollbackStrategy()
    
    override fun setSnapshotRollbackStrategy(strategy: RollbackStrategy) {
        configRepo.setSnapshotRollbackStrategy(strategy)
    }
}
