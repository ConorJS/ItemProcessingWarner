package itemprocessingwarner;

import com.aeimo.itemprocessingwarner.ItemProcessingWarnerPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ItemProcessingWarnerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ItemProcessingWarnerPlugin.class);
		RuneLite.main(args);
	}
}