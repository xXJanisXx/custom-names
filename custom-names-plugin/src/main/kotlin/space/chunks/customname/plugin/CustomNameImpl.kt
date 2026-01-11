package space.chunks.customname.plugin

import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import space.chunks.customname.api.CustomName
import space.chunks.customname.plugin.util.SkeletonInteraction
import java.util.function.Consumer

class CustomNameImpl(
    private val plugin: JavaPlugin,
    private val targetEntity: Entity
) : CustomName {

    private val interaction = SkeletonInteraction(this)

    private var effectiveHeight = 0.0
    private var passengerOffset = 0.0
    private var nametagEntityId = 0
    private var targetEntitySneaking = false
    private var hidden = false

    private var nameCallback: (viewer: Player) -> Component? = { null }
    private var task: BukkitTask? = null

    init {
        this.nametagEntityId = net.minecraft.world.entity.Entity.nextEntityId()

        val nmsEntity = (targetEntity as CraftEntity).handle

        val ridingOffset = nmsEntity
            .getPassengerRidingPosition(nmsEntity)
            .subtract(nmsEntity.position()).y

        val nametagOffset = nmsEntity.type.dimensions.height + 0.5f

        this.effectiveHeight = -ridingOffset - 0.5 + nametagOffset
        this.passengerOffset = ridingOffset

        this.task = object : BukkitRunnable() {
            override fun run() {
                val riderPacket: Packet<ClientGamePacketListener> =
                    this@CustomNameImpl.interaction.getRiderPacket()

                for (player in targetEntity.trackedPlayers) {
                    (player as CraftPlayer).handle.connection.send(riderPacket)
                }
            }
        }.runTaskTimer(plugin, 20, 20)
    }

    override fun setName(nameCallback: (viewer: Player) -> Component?) {
        this.nameCallback = nameCallback
        this.syncData()
    }

    override fun setTargetEntitySneaking(targetEntitySneaking: Boolean) {
        this.targetEntitySneaking = targetEntitySneaking
        this.syncData()
    }

    override fun setHidden(hidden: Boolean) {
        this.hidden = hidden
        this.runOnTrackers { player ->
            if (hidden) removeFromClient(player) else sendToClient(player)
        }
    }

    fun sendToClient(entity: Player) {
        if (!hidden) {
            (entity as CraftPlayer).handle.connection
                .send(interaction.initialSpawnPacket(entity))
        }
    }

    fun removeFromClient(entity: Player) {
        (entity as CraftPlayer).handle.connection.send(interaction.removePacket())
    }

    override fun getName(viewer: Player): Component? = nameCallback(viewer)
    override fun getNametagId(): Int = nametagEntityId
    override fun getTargetEntity(): Entity = targetEntity
    override fun isTargetEntitySneaking(): Boolean = targetEntitySneaking
    override fun getEffectiveHeight(): Double = effectiveHeight
    override fun getPassengerOffset(): Double = passengerOffset
    override fun isHidden(): Boolean = hidden

    private fun syncData() {
        if (hidden) return

        runOnTrackers { player ->
            val packet = interaction.syncDataPacket(player)
            (player as CraftPlayer).handle.connection.send(packet)
        }
    }

    private fun runOnTrackers(consumer: Consumer<Player>) {
        for (player in targetEntity.trackedBy) {
            consumer.accept(player)
        }
    }

    fun close() {
        task?.cancel()
    }
}
