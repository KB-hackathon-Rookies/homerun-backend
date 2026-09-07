import unittest
from pathlib import Path

from scripts.ingest import parse_front_matter, read_document


ROOT = Path(__file__).parents[1] / "data" / "corpus"


class CorpusQualityTest(unittest.TestCase):
    CASES = [
        ("01_first/youth_beotimmok.md", "FIRST", ("연령", "소득", "순자산")),
        ("01_first/diagnosis_inputs_and_resume.md", "FIRST", ("스텝", "저장", "이어")),
        ("02_second/registry_and_building_risks.md", "SECOND", ("등기부", "위반건축물", "선순위")),
        ("02_second/guarantee_and_official_price.md", "SECOND", ("공시가격", "반환보증", "126")),
        ("03_third/contract_schedule.md", "THIRD", ("D-21", "D-14", "잔금")),
        ("04_home/cashflow_and_delinquency.md", "HOME", ("월 잔여금", "고정지출", "연체")),
        ("04_home/renewal_and_moveout.md", "HOME", ("갱신", "퇴거", "보증금")),
    ]

    def test_representative_questions_keep_required_evidence(self):
        for relative, expected_stage, required_terms in self.CASES:
            with self.subTest(document=relative):
                path = ROOT / relative
                metadata, body = parse_front_matter(read_document(path), path.stem)
                self.assertEqual(expected_stage, metadata["stage"])
                for term in required_terms:
                    self.assertIn(term, body)


if __name__ == "__main__":
    unittest.main()
