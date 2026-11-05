import json
import re
import unittest
from datetime import date
from pathlib import Path
from urllib.parse import urlparse


REGISTRY_PATH = Path(__file__).with_name("registry.json")
REQUIRED_DATASET_FIELDS = {
    "id",
    "title",
    "version",
    "landing_page",
    "license",
    "license_url",
    "license_verified_at",
    "source_last_checked",
    "content_rights_status",
    "citation_url",
    "access",
    "commercial_use_status",
    "app_bundling_status",
    "external_ai_transfer_status",
    "modalities",
    "population",
    "permitted_use_in_this_project",
    "known_limitations",
    "required_citation",
}


class DatasetRegistryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))

    def test_registry_ids_are_unique_and_rights_fields_complete(self):
        datasets = self.registry["datasets"]
        ids = [dataset["id"] for dataset in datasets]

        self.assertEqual(len(ids), len(set(ids)))
        self.assertGreaterEqual(self.registry["schema_version"], 2)
        self.assertEqual(self.registry["project_policy"]["app_bundling"], "never_bundle_research_datasets")
        for dataset in datasets:
            self.assertTrue(REQUIRED_DATASET_FIELDS.issubset(dataset))
            self.assertTrue(
                any(
                    marker in dataset["app_bundling_status"]
                    for marker in ("not_allowed_by_project_policy", "prohibited_by_project_policy")
                ),
                dataset["id"],
            )
            self.assertTrue(dataset["license_verified_at"])

    def test_research_environment_dependencies_are_exactly_pinned(self):
        directory = REGISTRY_PATH.parent
        direct = (directory / "requirements.txt").read_text(encoding="utf-8").splitlines()
        locked = (directory / "requirements-lock.txt").read_text(encoding="utf-8").splitlines()
        exact_pin = re.compile(r"^[A-Za-z0-9_.-]+==[A-Za-z0-9_.+-]+$")

        self.assertTrue(direct)
        self.assertTrue(locked)
        self.assertTrue(all(exact_pin.fullmatch(line) for line in direct + locked))
        self.assertTrue(set(direct).issubset(set(locked)))
        self.assertEqual((directory / ".python-version").read_text(encoding="utf-8").strip(), "3.12.14")

    def test_restricted_dataset_is_not_misrepresented_as_product_cleared(self):
        mimic = next(item for item in self.registry["datasets"] if item["id"] == "physionet-mimic-iv-3.1")

        self.assertIn("restricted", mimic["access"])
        self.assertIn("not cleared", mimic["commercial_use_status"])
        self.assertIn("prohibited", mimic["external_ai_transfer_status"])
        self.assertIsNone(mimic["download_url"])
        self.assertIn("not currently accessed", mimic["permitted_use_in_this_project"])

    def test_dataset_catalog_has_valid_sources_dates_and_nonempty_scope(self):
        for dataset in self.registry["datasets"]:
            with self.subTest(dataset=dataset["id"]):
                self.assertTrue(dataset["id"].strip())
                self.assertTrue(dataset["title"].strip())
                self.assertTrue(dataset["version"].strip())
                self.assertEqual(urlparse(dataset["landing_page"]).scheme, "https")
                self.assertEqual(urlparse(dataset["license_url"]).scheme, "https")
                self.assertEqual(date.fromisoformat(dataset["license_verified_at"]).isoformat(), dataset["license_verified_at"])
                self.assertEqual(date.fromisoformat(dataset["source_last_checked"]).isoformat(), dataset["source_last_checked"])
                self.assertEqual(urlparse(dataset["citation_url"]).scheme, "https")
                self.assertIsInstance(dataset["modalities"], list)
                self.assertTrue(dataset["modalities"])
                self.assertIsInstance(dataset["known_limitations"], list)
                self.assertTrue(dataset["known_limitations"])
                self.assertTrue(dataset["permitted_use_in_this_project"].strip())
                for field in (
                    "commercial_use_status",
                    "app_bundling_status",
                    "external_ai_transfer_status",
                    "required_citation",
                ):
                    self.assertTrue(dataset[field].strip(), field)

                if dataset.get("download_url"):
                    self.assertEqual(urlparse(dataset["download_url"]).scheme, "https")
                if dataset.get("sha256_manifest_url"):
                    self.assertEqual(urlparse(dataset["sha256_manifest_url"]).scheme, "https")

    def test_registry_policy_does_not_grant_external_model_transfer_or_user_data_training(self):
        policy = self.registry["project_policy"]
        self.assertEqual(policy["app_bundling"], "never_bundle_research_datasets")
        self.assertTrue(policy["external_model_transfer"].startswith("not_planned;"))
        self.assertIn("rights and privacy review", policy["external_model_transfer"])
        self.assertIn("not_permitted", policy["user_health_data_training"])

    def test_wrist_dataset_rate_is_not_misrepresented_as_shared_clock(self):
        wrist = next(item for item in self.registry["datasets"] if item["id"] == "physionet-wrist-ppg-exercise-1.0.0")

        self.assertEqual(wrist["nominal_record_sampling_hz"], 256)
        self.assertNotIn("sampling_hz", wrist)
        self.assertIn("does not establish a shared physical timebase", wrist["sampling_metadata_note"])

    def test_mimic_registry_has_version_specific_doi_and_unresolved_rights(self):
        mimic = next(item for item in self.registry["datasets"] if item["id"] == "physionet-mimic-iv-3.1")

        self.assertEqual(mimic["doi"], "10.13026/kpb9-mt58")
        self.assertIn("credentialed license", mimic["content_rights_status"])
        self.assertIn("no commercial", mimic["content_rights_status"])

    def test_sleep_cohort_counts_distinguish_recruited_and_included(self):
        sleep = next(item for item in self.registry["datasets"] if item["id"] == "physionet-sleep-accel-1.0.0")

        self.assertIn("39 recruited; 8 excluded; 31 included", sleep["population"])


if __name__ == "__main__":
    unittest.main()
