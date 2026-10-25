
"""Inspect generated real dependency bytecode; does not start or mutate a server."""
import argparse, pathlib, subprocess, zipfile

def inspect(jar, cls):
    return subprocess.check_output(['javap', '-p', '-c', '-classpath', str(jar), cls], text=True)

def main():
    a = argparse.ArgumentParser()
    a.add_argument('case')
    a.add_argument('original', type=pathlib.Path)
    a.add_argument('patched', type=pathlib.Path)
    x = a.parse_args()
    with zipfile.ZipFile(x.original) as original, zipfile.ZipFile(x.patched) as patched:
        if x.case == 'residence':
            s = inspect(x.patched, 'com.bekvon.bukkit.residence.protection.ResidencePermissions')
            assert s.count('org/nomiteam/vip/compat/ResidenceGrant.grants:') == 2
            assert 'NuomiResidenceFlight.grants:' not in s
            assert patched.read('LICENSE') == original.read('LICENSE')
            helper = inspect(x.patched, 'org.nomiteam.vip.compat.ResidenceGrant')
            assert 'containsLoc:' in helper and 'isDisabledWorldListener:' in helper
        elif x.case == 'price':
            s = inspect(x.patched, 'cn.handyplus.warp.inventory.CreateGui')
            assert s.count('org/nomiteam/vip/compat/WarpPricing.quote:') == 1
            assert 'NuomiWarpPricing' not in s
            assert 'WarpPricing.price:' not in s
            assert 'WarpPricing.remaining:' not in s
            pos = s.index('WarpPricing.quote:')
            assert 'lstore' in s[pos:pos + 200]
        elif x.case == 'payments':
            s = inspect(x.patched, 'cn.handyplus.warp.listener.gui.CreateClickEvent')
            for n in ['takePoints', 'takeVault', 'takeCurrency']:
                assert s.count('WarpPricing.' + n + ':') == 1
            assert 'NuomiWarpPricing' not in s
        elif x.case == 'paid':
            s = inspect(x.patched, 'cn.handyplus.warp.util.WarpUtil')
            start = s.index('public static boolean checkWarpNumber')
            end = s.index('\n  public ', start + 5)
            s = s[start:end]
            assert 'String nuomi.warp.paid' not in s
            assert s.count('WarpPricing.limit:') == 1
            assert 'isCreateFlag:' in s and 'getWorld:' in s
        elif x.case == 'serialized':
            s = inspect(x.patched, 'cn.handyplus.warp.listener.gui.CreateClickEvent')
            assert 'nomi$rawSlotClick' in s and 'monitorenter' in s and (s.count('monitorexit') == 2)
            assert 'WarpPricing.lock:' in s
        elif x.case == 'quote':
            s = inspect(x.patched, 'org.nomiteam.vip.compat.WarpPricing')
            assert 'QuoteBook.consume:' in s and 'QuoteBook.offer:' in s
        elif x.case == 'unchanged':
            changed = {'com/bekvon/bukkit/residence/protection/ResidencePermissions.class', 'cn/handyplus/warp/inventory/CreateGui.class', 'cn/handyplus/warp/listener/gui/CreateClickEvent.class', 'cn/handyplus/warp/util/WarpUtil.class'}
            removed = {'com/bekvon/bukkit/residence/protection/NuomiResidenceFlight.class', 'cn/handyplus/warp/util/NuomiWarpPricing.class'}
            for n in original.namelist():
                if n not in changed | removed:
                    assert original.read(n) == patched.read(n), n
        else:
            raise AssertionError(x.case)
    print('PASS compat ' + x.case)
if __name__ == '__main__':
    main()
