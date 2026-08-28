package cn.ksmcbrigade.cfmsF.mixin;

import cn.ksmcbrigade.cfmsF.transformers.ArrayListTransformer;
import cn.ksmcbrigade.cfmsF.utils.AgentUtils;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Set;

public class LocatorMixinPlugin implements IMixinConfigPlugin {

    static {
        Log.info(LogCategory.MIXIN,"CFMSf Mixin Plugin Start!");

        Instrumentation instrumentation = AgentUtils.getInst();
        if(instrumentation!=null && ArrayListTransformer.bytes!=null){
            try {
                ArrayListTransformer.transformed = false;
                ClassFileTransformer transformer = new ArrayListTransformer(true);
                instrumentation.addTransformer(transformer,true);
                instrumentation.retransformClasses(Class.forName("java.util.ArrayList$Itr"));
                instrumentation.removeTransformer(transformer);
            } catch (Throwable e) {
               Log.error(LogCategory.MIXIN,"Failed to restore ArrayList$Itr.This may case some bugs.",e);
            }
        }
        else{
            Log.warn(LogCategory.MIXIN,"Cannot get inst or original class buffers to restore ArrayList$Itr. This may case some bugs.");
        }

        Log.info(LogCategory.MIXIN,"CFMSf End!");
    }

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
