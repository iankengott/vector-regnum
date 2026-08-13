/**
 * Immutable automation messages and pure redstone rules.
 *
 * <p>Ownership contract: producers own only their local values until submit;
 * submitted frames are immutable; {@link vectorregnum.core.automation.AutomationScheduler}
 * owns its bounded queue; the server tick exclusively owns dequeue, Minecraft
 * state, spell VMs, mana, and result publication. A cast never shares a mutable
 * stack or VM with another cast.</p>
 */
package vectorregnum.core.automation;
