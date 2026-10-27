"""Deletion and isolation gate for the simplified runtime artifact."""
import pathlib
import sys
import zipfile

jar = pathlib.Path(sys.argv[1])
removed = (
    'ScaleControl', 'VipPolicy', 'DeluxeHubJump', 'JumpAuthorization', 'ResidenceHook',
    'CompatibilityReport', 'FlightPolicy', 'FlightTransition', 'PolicyChecks',
    'MembershipIdentity', 'MembershipNameCommand',
)
with zipfile.ZipFile(jar) as archive:
    names = archive.namelist()
    for name in names:
        assert not any(removed_class in name for removed_class in removed), name
        if name.endswith('.class'):
            data = archive.read(name)
            for reference in (b'com/bekvon/bukkit/residence', b'org/objectweb/asm',
                              b'cn/handyplus/warp', b'DeluxeHub', b'java/lang/reflect',
                              b'org/bukkit/attribute'):
                assert reference not in data, (name, reference)
    descriptor = archive.read('plugin.yml').decode()
    assert 'softdepend: [LuckPerms]' in descriptor
    assert 'nomivipgrant:' in descriptor and 'aliases: [fly]' in descriptor
    assert 'nuomiscale:' not in descriptor and 'nomivipgrantname:' not in descriptor
    for node in ('nuomi.warp.free.2', 'nuomi.warp.free.4', 'nuomi.warp.free.6', 'nuomi.warp.paid'):
        assert node in descriptor
print('PASS simplified artifact: no scale/DH/Residence/QR bridge classes or foreign API references; '
      'UUID grant and warp permission defaults preserved')
