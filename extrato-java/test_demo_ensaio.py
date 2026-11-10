import tempfile
import unittest
from pathlib import Path

from demo_ensaio import gate


class DemoEnsaioTest(unittest.TestCase):
    def test_gate_so_cria_sentinela_para_rc_zero_e_bytes_iguais(self):
        with tempfile.TemporaryDirectory() as diretorio:
            pasta = Path(diretorio)
            esperado = b"referencia sintetica"
            for rc in (4, 12, -9):
                sentinela = pasta / f"rc-{rc}"
                self.assertEqual("BLOQUEADO_RC", gate(rc, esperado, esperado, sentinela))
                self.assertFalse(sentinela.exists())
            sentinela = pasta / "diferente"
            self.assertEqual("BLOQUEADO_DIVERGENCIA", gate(0, b"outro", esperado, sentinela))
            self.assertFalse(sentinela.exists())
            sentinela = pasta / "igual"
            self.assertEqual("PERMITIDO_SIMULACAO", gate(0, esperado, esperado, sentinela))
            self.assertEqual(b"SIMULACAO SEM ENTREGA\n", sentinela.read_bytes())
            with self.assertRaises(FileExistsError):
                gate(0, esperado, esperado, sentinela)
            self.assertEqual(b"SIMULACAO SEM ENTREGA\n", sentinela.read_bytes())


if __name__ == "__main__":
    unittest.main()
