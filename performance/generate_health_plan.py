"""Create a JMeter 5.6.x plan for the public GET /api/health endpoint."""
from pathlib import Path
import xml.etree.ElementTree as ET


OUT = Path(__file__).with_name("health-load.jmx")
LEVELS = ((10, 2), (100, 20), (10000, 200))


def prop(parent, tag, name, value):
    child = ET.SubElement(parent, tag, {"name": name})
    child.text = str(value).lower() if isinstance(value, bool) else str(value)
    return child


def node(parent, tag, gui, name):
    item = ET.SubElement(parent, tag, {"guiclass": gui, "testclass": tag, "testname": name})
    branch = ET.SubElement(parent, "hashTree")
    return item, branch


def save_config(collector):
    obj = prop(collector, "objProp", "saveConfig", "")
    ET.SubElement(obj, "name").text = "saveConfig"
    value = ET.SubElement(obj, "value", {"class": "SampleSaveConfiguration"})
    fields = {
        "time": True, "latency": True, "timestamp": True, "success": True,
        "label": True, "code": True, "message": True, "threadName": True,
        "dataType": True, "encoding": False, "assertions": True,
        "subresults": True, "responseData": False, "samplerData": False,
        "xml": False, "fieldNames": True, "responseHeaders": False,
        "requestHeaders": False, "responseDataOnError": False,
        "saveAssertionResultsFailureMessage": True, "assertionsResultsToSave": 0,
        "bytes": True, "sentBytes": True, "url": True,
        "threadCounts": True, "idleTime": True, "connectTime": True,
    }
    for key, val in fields.items():
        ET.SubElement(value, key).text = str(val).lower() if isinstance(val, bool) else str(val)


def main():
    root = ET.Element("jmeterTestPlan", {"version": "1.2", "properties": "5.0", "jmeter": "5.6.3"})
    tree = ET.SubElement(root, "hashTree")
    plan, plan_tree = node(tree, "TestPlan", "TestPlanGui", "Health API load")
    variables = ET.SubElement(plan, "elementProp", {"name": "TestPlan.user_defined_variables", "elementType": "Arguments", "guiclass": "ArgumentsPanel", "testclass": "Arguments", "testname": "User Defined Variables"})
    ET.SubElement(variables, "collectionProp", {"name": "Arguments.arguments"})
    prop(plan, "boolProp", "TestPlan.functional_mode", False)
    prop(plan, "boolProp", "TestPlan.serialize_threadgroups", True)

    for users, ramp in LEVELS:
        group, group_tree = node(plan_tree, "ThreadGroup", "ThreadGroupGui", f"Health Load {users}")
        prop(group, "intProp", "ThreadGroup.num_threads", users)
        prop(group, "intProp", "ThreadGroup.ramp_time", ramp)
        prop(group, "boolProp", "ThreadGroup.same_user_on_next_iteration", True)
        prop(group, "stringProp", "ThreadGroup.on_sample_error", "continue")
        loop = ET.SubElement(group, "elementProp", {"name": "ThreadGroup.main_controller", "elementType": "LoopController", "guiclass": "LoopControlPanel", "testclass": "LoopController", "testname": "Loop Controller"})
        prop(loop, "stringProp", "LoopController.loops", 1)
        prop(loop, "boolProp", "LoopController.continue_forever", False)

        request, request_tree = node(group_tree, "HTTPSamplerProxy", "HttpTestSampleGui", f"Health {users}")
        for key, value in (("connect_timeout", 10000), ("response_timeout", 30000)):
            prop(request, "intProp", f"HTTPSampler.{key}", value)
        for key, value in (("domain", "127.0.0.1"), ("port", "8080"), ("protocol", "http"), ("path", "/api/health"), ("method", "GET")):
            prop(request, "stringProp", f"HTTPSampler.{key}", value)
        prop(request, "boolProp", "HTTPSampler.follow_redirects", False)
        prop(request, "boolProp", "HTTPSampler.use_keepalive", True)
        args = ET.SubElement(request, "elementProp", {"name": "HTTPsampler.Arguments", "elementType": "Arguments"})
        ET.SubElement(args, "collectionProp", {"name": "Arguments.arguments"})

        assertion, _ = node(request_tree, "ResponseAssertion", "AssertionGui", "HTTP 200")
        values = ET.SubElement(assertion, "collectionProp", {"name": "Asserion.test_strings"})
        ET.SubElement(values, "stringProp", {"name": "1"}).text = "200"
        prop(assertion, "stringProp", "Assertion.test_field", "Assertion.response_code")
        prop(assertion, "boolProp", "Assertion.assume_success", False)
        prop(assertion, "intProp", "Assertion.test_type", 8)

        json_assertion, _ = node(request_tree, "JSONPathAssertion", "JSONPathAssertionGui", "status UP")
        prop(json_assertion, "stringProp", "JSON_PATH", "$.status")
        prop(json_assertion, "stringProp", "EXPECTED_VALUE", "UP")
        prop(json_assertion, "boolProp", "JSONVALIDATION", True)
        prop(json_assertion, "boolProp", "EXPECT_NULL", False)
        prop(json_assertion, "boolProp", "INVERT", False)
        prop(json_assertion, "boolProp", "ISREGEX", False)

        collector, _ = node(group_tree, "ResultCollector", "StatVisualizer", f"Aggregate Health {users}")
        prop(collector, "boolProp", "ResultCollector.error_logging", False)
        save_config(collector)
        prop(collector, "stringProp", "filename", str(OUT.with_name(f"health-{users}.jtl")))

    collector, _ = node(plan_tree, "ResultCollector", "StatVisualizer", "Aggregate Health All")
    prop(collector, "boolProp", "ResultCollector.error_logging", False)
    save_config(collector)
    prop(collector, "stringProp", "filename", str(OUT.with_name("health-all-levels.jtl")))
    ET.indent(root)
    OUT.write_bytes(ET.tostring(root, encoding="utf-8", xml_declaration=True))
    print(OUT)


if __name__ == "__main__":
    main()
