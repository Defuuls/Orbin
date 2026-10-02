import os
import re

impl_dir = "core/data/src/commonMain/kotlin/com/orbin/data/repository"
impl_files = [f for f in os.listdir(impl_dir) if f.endswith("Impl.kt")]

graph_additions = []
module_additions = []

for f in impl_files:
    class_name = f.replace(".kt", "")
    interface_name = class_name.replace("Impl", "")
    var_name = interface_name[0].lower() + interface_name[1:]
    
    graph_additions.append(f"    abstract val {var_name}: com.orbin.domain.repository.{interface_name}")
    graph_additions.append(f"    @Provides\n    @AppScope\n    protected fun {var_name}(impl: com.orbin.data.repository.{class_name}): com.orbin.domain.repository.{interface_name} = impl")
    
    module_additions.append(f"    @Provides\n    fun provides{interface_name}(graph: SharedGraph): com.orbin.domain.repository.{interface_name} = graph.{var_name}")

print("GRAPH:")
print("\n".join(graph_additions))
print("\nMODULE:")
print("\n".join(module_additions))
